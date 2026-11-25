package dev.ae2security.gametest;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.parts.PartHelper;
import appeng.api.util.AEColor;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import com.mojang.authlib.GameProfile;
import dev.ae2security.MESecurity;
import dev.ae2security.part.SecurityTerminalPart;
import dev.ae2security.security.MenuAccess;
import dev.ae2security.security.Permission;
import dev.ae2security.security.SecurityAccess;
import dev.ae2security.security.SecurityBreak;
import dev.ae2security.security.SecurityHardware;
import dev.ae2security.security.SecuritySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Add-on grid hardware is protected, unknown add-on menus need Build, and native AE2 menus keep their rules. */
@GameTestHolder(MESecurity.ID)
@PrefixGameTestTemplate(false)
public final class AddonHardwareGameTests {
    /** Every fixture stays within 0..4 on each axis so neighboring tests can never join its networks. */
    private static final BlockPos CENTER = new BlockPos(2, 1, 2);

    /** Every AE2 19.2.17 menu type and the access it had when the classification was hard-coded. */
    private static final Map<MenuAccess, Set<String>> NATIVE_MENUS = Map.of(
            MenuAccess.OWNER, Set.of("cellworkbench", "controller_networkstatus", "networkstatus", "networktool",
                    "pattern_provider", "patternaccessterminal", "patternterm", "quartzknife", "skychest"),
            MenuAccess.HARDWARE, Set.of("drive", "me_chest", "ioport", "priority", "storagebus", "import_bus",
                    "export_bus", "formationplane", "interface", "set_stock_amount", "molecular_assembler",
                    "energy_level_emitter", "storage_level_emitter", "qnb", "spatialanchor", "spatialioport",
                    "wireless_access_point", "vibrationchamber", "inscriber", "condenser"),
            MenuAccess.STORAGE, Set.of("item_terminal", "craftingterm", "wirelessterm", "wirelesscraftingterm",
                    "basic_cell_chest", "portable_item_cell", "portable_fluid_cell"),
            MenuAccess.CRAFTING_REQUEST, Set.of("craftamount", "craftconfirm"),
            MenuAccess.STATUS, Set.of("craftingcpu", "craftingstatus"));

    private AddonHardwareGameTests() {}

    private static ServerPlayer player(ServerLevel level, String name, BlockPos pos) {
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), name));
        player.setPos(Vec3.atCenterOf(pos));
        return player;
    }

    private static SecurityTerminalPart terminal(ServerLevel level, BlockPos pos, ServerPlayer owner) {
        PartHelper.setPart(level, pos, null, owner, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        var terminal = PartHelper.setPart(level, pos, Direction.NORTH, owner, MESecurity.TERMINAL_ITEM.get());
        if (terminal == null) throw new IllegalStateException("Could not place the GameTest security terminal");
        return terminal;
    }

    private static void trust(ServerLevel level, SecurityTerminalPart terminal, ServerPlayer owner,
            ServerPlayer player, int mask) {
        var data = SecuritySavedData.get(level.getServer());
        var id = terminal.terminalId();
        data.update(id, data.policy(id).trust(owner.getUUID(), player.getUUID(), mask));
    }

    /**
     * Stands in for an add-on's full block: a block entity outside the ae2 namespace and outside AE2's networked
     * block-entity classes that only implements AE2's public node-host API. It is never registered or placed.
     */
    private static final class AddonHost extends BlockEntity implements IInWorldGridNodeHost {
        private final IGridNode node;

        AddonHost(BlockPos pos, IGridNode node) {
            super(BlockEntityType.CHEST, pos, Blocks.CHEST.defaultBlockState());
            this.node = node;
        }

        @Override
        public IGridNode getGridNode(Direction side) {
            return node;
        }
    }

    /** No ae2 menu type is left to the Build fallback, and each keeps the access it had before the tags. */
    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void nativeMenuClassificationIsUnchanged(GameTestHelper helper) {
        int registered = 0;
        for (var id : BuiltInRegistries.MENU.keySet()) {
            if (!id.getNamespace().equals("ae2")) continue;
            registered++;
            var access = MenuAccess.of(id);
            helper.assertTrue(access != MenuAccess.FALLBACK, id + " must be classified explicitly");
            helper.assertTrue(NATIVE_MENUS.get(access).contains(id.getPath()), id + " changed access to " + access);
        }
        int expected = NATIVE_MENUS.values().stream().mapToInt(Set::size).sum();
        helper.assertValueEqual(expected, registered, "every AE2 19.2.17 menu type is covered");
        helper.assertValueEqual(MenuAccess.FALLBACK, MenuAccess.of(ResourceLocation.parse("minecraft:generic_9x3")),
                "an untagged menu type falls back to Build");
        helper.succeed();
    }

    /** A Drive still needs Build, the pattern provider stays owner-only, a terminal needs View, and add-on menus need Build. */
    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void nativeMenusUnchangedAndUnknownMenusNeedBuild(GameTestHelper helper) {
        var level = helper.getLevel();
        var hub = helper.absolutePos(CENTER);
        var owner = player(level, "AddonOwner", hub);
        var viewer = player(level, "AddonViewer", hub);
        var builder = player(level, "AddonBuilder", hub);
        var outsider = player(level, "AddonOutsider", hub);
        var terminal = terminal(level, hub, owner);
        var storageTerminal = PartHelper.setPart(level, hub, Direction.SOUTH, owner, AEParts.TERMINAL.get());
        level.setBlockAndUpdate(hub.east(), AEBlocks.DRIVE.block().defaultBlockState());
        level.setBlockAndUpdate(hub.west(), AEBlocks.PATTERN_PROVIDER.block().defaultBlockState());
        helper.runAfterDelay(20, () -> {
            trust(level, terminal, owner, viewer, 0);
            trust(level, terminal, owner, builder, Permission.ALL);
            var drive = (DriveBlockEntity) level.getBlockEntity(hub.east());
            var provider = (PatternProviderBlockEntity) level.getBlockEntity(hub.west());
            helper.assertTrue(SecurityHardware.protectedBlock(level, hub.east())
                    && SecurityHardware.protectedBlock(level, hub.west()), "fixture hardware must be protected");

            var driveMenu = ResourceLocation.parse("ae2:drive");
            helper.assertFalse(SecurityAccess.opening(viewer, drive, driveMenu), "the drive menu still needs Build");
            helper.assertTrue(SecurityAccess.opening(builder, drive, driveMenu), "Build opens the drive menu");

            var providerMenu = ResourceLocation.parse("ae2:pattern_provider");
            helper.assertFalse(SecurityAccess.opening(builder, provider, providerMenu),
                    "the pattern provider stays owner-only, even with every permission");
            helper.assertTrue(SecurityAccess.opening(owner, provider, providerMenu), "the owner opens the pattern provider");

            var terminalMenu = ResourceLocation.parse("ae2:item_terminal");
            helper.assertTrue(SecurityAccess.opening(viewer, storageTerminal, terminalMenu), "a terminal needs only View");
            helper.assertFalse(SecurityAccess.opening(outsider, storageTerminal, terminalMenu), "a terminal still needs trust");

            for (var addonMenu : new ResourceLocation[] {ResourceLocation.parse("minecraft:generic_9x3"),
                    ResourceLocation.fromNamespaceAndPath("ae2security", "unregistered_addon_machine")}) {
                helper.assertFalse(SecurityAccess.opening(viewer, drive, addonMenu), addonMenu + " needs more than View");
                helper.assertFalse(SecurityAccess.opening(outsider, drive, addonMenu), addonMenu + " refuses outsiders");
                helper.assertTrue(SecurityAccess.opening(builder, drive, addonMenu), addonMenu + " opens with Build");
                helper.assertFalse(SecurityAccess.opening(viewer, storageTerminal, addonMenu),
                        addonMenu + " on a part needs Build");
                helper.assertTrue(SecurityAccess.opening(builder, storageTerminal, addonMenu),
                        addonMenu + " on a part opens with Build");
            }
            level.setBlockAndUpdate(hub.east(), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(hub.west(), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(hub, Blocks.AIR.defaultBlockState());
            helper.succeed();
        });
    }

    /** Hardware outside the ae2 namespace is judged by the grid it hosts, and a host without a node fails closed. */
    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void addonGridHostsAreProtected(GameTestHelper helper) {
        var level = helper.getLevel();
        var hub = helper.absolutePos(CENTER);
        var isolated = helper.absolutePos(new BlockPos(0, 1, 0));
        var owner = player(level, "AddonHostOwner", hub);
        var viewer = player(level, "AddonHostViewer", hub);
        var builder = player(level, "AddonHostBuilder", hub);
        var outsider = player(level, "AddonHostOutsider", hub);
        var terminal = terminal(level, hub, owner);
        var publicCable = PartHelper.setPart(level, isolated, null, owner, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        helper.runAfterDelay(20, () -> {
            trust(level, terminal, owner, viewer, Permission.INSERT.bit() | Permission.EXTRACT.bit());
            trust(level, terminal, owner, builder, Permission.BUILD.bit());
            var protectedHost = new AddonHost(hub.above(), terminal.getGridNode());
            var publicHost = new AddonHost(isolated.above(), publicCable.getGridNode());
            var pendingHost = new AddonHost(hub.above(2), null);
            helper.assertFalse(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(protectedHost.getType()).getNamespace().equals("ae2"),
                    "the stand-in host must lie outside the ae2 namespace");
            helper.assertTrue(SecurityHardware.gridHost(protectedHost), "a node host from any mod is grid hardware");

            helper.assertFalse(SecurityBreak.allowBlockBreak(protectedHost, outsider), "outsiders cannot mine add-on hardware");
            helper.assertFalse(SecurityBreak.allowBlockBreak(protectedHost, viewer), "other permissions cannot mine it");
            helper.assertTrue(SecurityBreak.allowBlockBreak(protectedHost, builder), "Build mines add-on hardware");
            helper.assertTrue(SecurityBreak.allowBlockBreak(protectedHost, owner), "the owner mines add-on hardware");
            helper.assertTrue(SecurityBreak.allowBlockBreak(publicHost, outsider), "add-on hardware on a public grid stays public");
            helper.assertFalse(SecurityBreak.allowBlockBreak(pendingHost, owner), "a host with no node yet is unresolved");

            level.setBlockAndUpdate(hub, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(isolated, Blocks.AIR.defaultBlockState());
            helper.succeed();
        });
    }
}
