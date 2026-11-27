package dev.ae2security.gametest;

import java.util.UUID;

import appeng.api.parts.PartHelper;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEParts;
import com.mojang.authlib.GameProfile;
import dev.ae2security.MESecurity;
import dev.ae2security.integration.SecurityTerminalTooltip;
import dev.ae2security.integration.SecurityTerminalTooltip.Shown;
import dev.ae2security.integration.SecurityTerminalTooltip.Status;
import dev.ae2security.part.SecurityTerminalPart;
import dev.ae2security.security.Permission;
import dev.ae2security.security.SecuritySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The Jade and WTHIT tooltip data a real, placed Security Terminal sends to each kind of player. */
@GameTestHolder(MESecurity.ID)
@PrefixGameTestTemplate(false)
public final class TooltipGameTests {
    private static final BlockPos ORIGIN = BlockPos.ZERO;

    private TooltipGameTests() {}

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void tooltipShowsTheOwnerToEveryoneAndAccessToTheViewer(GameTestHelper helper) {
        var level = helper.getLevel();
        var position = helper.absolutePos(ORIGIN);
        var ownerName = "TooltipOwner" + UUID.randomUUID().toString().substring(0, 6);
        var owner = player(level, ownerName);
        var trusted = player(level, "TooltipTrusted");
        var stranger = player(level, "TooltipStranger");
        var data = SecuritySavedData.get(level.getServer());
        data.rememberPlayer(owner.getUUID(), ownerName);
        var terminal = terminal(level, position, owner);
        var id = terminal.terminalId();
        // A part joins the world, and gets its policy, on the first tick after its cable bus is placed.
        helper.runAfterDelay(5, () -> {
            data.update(id, data.policy(id).trust(owner.getUUID(), trusted.getUUID(), Permission.INSERT.bit()));

            helper.assertValueEqual(new Shown(Status.OWNER, ownerName, Permission.ALL), shown(owner, terminal),
                    "the owner sees owner access");
            helper.assertValueEqual(new Shown(Status.TRUSTED, ownerName, Permission.VIEW.bit() | Permission.INSERT.bit()),
                    shown(trusted, terminal), "a trusted player sees exactly their permissions");
            helper.assertValueEqual(new Shown(Status.NONE, ownerName, 0), shown(stranger, terminal),
                    "a stranger sees the owner's name and no access");

            data.retire(id);
            helper.assertValueEqual(new Shown(Status.RETIRED, null, 0), shown(stranger, terminal),
                    "a retired terminal names no owner");
            level.destroyBlock(position, false);
            helper.succeed();
        });
    }

    private static Shown shown(ServerPlayer player, SecurityTerminalPart terminal) {
        var tag = new CompoundTag();
        SecurityTerminalTooltip.provideServerData(player, terminal, tag);
        return Shown.fromTag(tag.getCompound("ae2security"));
    }

    private static ServerPlayer player(ServerLevel level, String name) {
        return FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), name));
    }

    private static SecurityTerminalPart terminal(ServerLevel level, BlockPos position, ServerPlayer owner) {
        var cable = PartHelper.setPart(level, position, null, owner, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        if (cable == null) throw new IllegalStateException("Could not place GameTest cable");
        var part = PartHelper.setPart(level, position, Direction.NORTH, owner, MESecurity.TERMINAL_ITEM.get());
        if (!(part instanceof SecurityTerminalPart terminal)) {
            throw new IllegalStateException("Could not place GameTest terminal");
        }
        return terminal;
    }
}
