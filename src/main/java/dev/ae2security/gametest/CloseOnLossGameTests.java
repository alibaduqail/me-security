package dev.ae2security.gametest;

import java.util.List;
import java.util.UUID;

import appeng.api.parts.PartHelper;
import appeng.api.util.AEColor;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import appeng.menu.implementations.DriveMenu;
import appeng.menu.me.common.MEStorageMenu;
import com.mojang.authlib.GameProfile;
import dev.ae2security.MESecurity;
import dev.ae2security.part.SecurityTerminalPart;
import dev.ae2security.security.Permission;
import dev.ae2security.security.SecurityAccess;
import dev.ae2security.security.SecuritySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Permission reductions close an open screen only when the lost permission matters to that screen. */
@GameTestHolder(MESecurity.ID)
@PrefixGameTestTemplate(false)
public final class CloseOnLossGameTests {
    private static final BlockPos CENTER = new BlockPos(2, 1, 2);
    private static final int STORAGE = Permission.VIEW.bit() | Permission.INSERT.bit() | Permission.EXTRACT.bit()
            | Permission.CRAFT.bit();

    private CloseOnLossGameTests() {}

    private static ServerPlayer player(ServerLevel level, String name, BlockPos pos) {
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), name));
        player.setPos(Vec3.atCenterOf(pos));
        return player;
    }

    private static SecurityTerminalPart terminal(ServerLevel level, BlockPos pos, ServerPlayer owner) {
        PartHelper.setPart(level, pos, null, owner, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        var terminal = PartHelper.setPart(level, pos, Direction.UP, owner, MESecurity.TERMINAL_ITEM.get());
        if (terminal == null) throw new IllegalStateException("Could not place the GameTest security terminal");
        return terminal;
    }

    /** FakePlayerFactory does not log players in; enroll one so policyChanged can see its open menu. */
    private static List<ServerPlayer> roster(ServerLevel level) {
        try {
            var field = PlayerList.class.getDeclaredField("players");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var players = (List<ServerPlayer>) field.get(level.getServer().getPlayerList());
            return players;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not enroll the GameTest player", e);
        }
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void storageMenuClosesOnlyForStorageBits(GameTestHelper helper) {
        var level = helper.getLevel();
        var hub = helper.absolutePos(CENTER);
        var owner = player(level, "CloseOwner", hub);
        var guest = player(level, "CloseGuest", hub);
        var terminal = terminal(level, hub, owner);
        var storageTerminal = PartHelper.setPart(level, hub, Direction.DOWN, owner, AEParts.TERMINAL.get());
        helper.runAfterDelay(20, () -> {
            var data = SecuritySavedData.get(level.getServer());
            var id = terminal.terminalId();
            var full = data.policy(id).trust(owner.getUUID(), guest.getUUID(), STORAGE);
            data.update(id, full);
            var players = roster(level);
            players.add(guest);
            try {
                guest.containerMenu = new MEStorageMenu(MEStorageMenu.TYPE, 2, guest.getInventory(), storageTerminal);
                var withoutCraft = full.trust(owner.getUUID(), guest.getUUID(), STORAGE & ~Permission.CRAFT.bit());
                data.update(id, withoutCraft);
                SecurityAccess.policyChanged(level.getServer(), id, full, withoutCraft);
                helper.assertTrue(guest.containerMenu instanceof MEStorageMenu,
                        "losing Craft must not close a storage terminal");
                var withoutExtract = withoutCraft.trust(owner.getUUID(), guest.getUUID(),
                        STORAGE & ~Permission.CRAFT.bit() & ~Permission.EXTRACT.bit());
                data.update(id, withoutExtract);
                SecurityAccess.policyChanged(level.getServer(), id, withoutCraft, withoutExtract);
                helper.assertTrue(guest.containerMenu == guest.inventoryMenu, "losing Extract must close a storage terminal");
            } finally {
                players.remove(guest);
                guest.containerMenu = guest.inventoryMenu;
            }
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void hardwareMenuClosesOnBuildLossOnly(GameTestHelper helper) {
        var level = helper.getLevel();
        var hub = helper.absolutePos(CENTER);
        var owner = player(level, "CloseHwOwner", hub);
        var guest = player(level, "CloseHwGuest", hub);
        var terminal = terminal(level, hub, owner);
        level.setBlockAndUpdate(hub.east(), AEBlocks.DRIVE.block().defaultBlockState());
        helper.runAfterDelay(30, () -> {
            var data = SecuritySavedData.get(level.getServer());
            var id = terminal.terminalId();
            int mask = Permission.BUILD.bit() | Permission.CRAFT.bit();
            var full = data.policy(id).trust(owner.getUUID(), guest.getUUID(), mask);
            data.update(id, full);
            var drive = (DriveBlockEntity) level.getBlockEntity(hub.east());
            var players = roster(level);
            players.add(guest);
            try {
                guest.containerMenu = new DriveMenu(5, guest.getInventory(), drive);
                var withoutCraft = full.trust(owner.getUUID(), guest.getUUID(), Permission.BUILD.bit());
                data.update(id, withoutCraft);
                SecurityAccess.policyChanged(level.getServer(), id, full, withoutCraft);
                helper.assertTrue(guest.containerMenu instanceof DriveMenu, "losing Craft must not close a hardware menu");
                var withoutBuild = withoutCraft.trust(owner.getUUID(), guest.getUUID(), 0);
                data.update(id, withoutBuild);
                SecurityAccess.policyChanged(level.getServer(), id, withoutCraft, withoutBuild);
                helper.assertTrue(guest.containerMenu == guest.inventoryMenu, "losing Build must close a hardware menu");
            } finally {
                players.remove(guest);
                guest.containerMenu = guest.inventoryMenu;
            }
            level.setBlockAndUpdate(hub.east(), Blocks.AIR.defaultBlockState());
            helper.succeed();
        });
    }
}
