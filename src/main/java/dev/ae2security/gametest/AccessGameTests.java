package dev.ae2security.gametest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import appeng.api.config.Actionable;
import appeng.api.config.CpuSelectionMode;
import appeng.api.config.Settings;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;
import appeng.api.storage.SupplierStorage;
import appeng.api.util.AEColor;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.core.network.serverbound.ConfigButtonPacket;
import appeng.items.contents.NetworkToolMenuHost;
import appeng.menu.locator.MenuLocators;
import appeng.menu.me.crafting.CraftingCPUMenu;
import appeng.parts.storagebus.StorageBusPart;
import com.mojang.authlib.GameProfile;
import dev.ae2security.MESecurity;
import dev.ae2security.part.SecurityTerminalPart;
import dev.ae2security.security.CraftingActionSource;
import dev.ae2security.security.Permission;
import dev.ae2security.security.PlayerMenuStorage;
import dev.ae2security.security.SecurityAccess;
import dev.ae2security.security.SecuritySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Player and cross-network access to storage, crafting controls and status screens. */
@GameTestHolder(MESecurity.ID)
@PrefixGameTestTemplate(false)
public final class AccessGameTests {
    /** Every fixture stays within 0..4 on each axis so neighboring tests can never join its networks. */
    private static final BlockPos CENTER = new BlockPos(2, 1, 2);
    private static final AEItemKey DIAMOND = AEItemKey.of(Items.DIAMOND);

    private AccessGameTests() {}

    private static ServerPlayer player(ServerLevel level, String name, BlockPos pos) {
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), name));
        player.setPos(Vec3.atCenterOf(pos));
        return player;
    }

    private static SecurityTerminalPart terminal(ServerLevel level, BlockPos pos, Direction side, ServerPlayer owner) {
        PartHelper.setPart(level, pos, null, owner, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        var terminal = PartHelper.setPart(level, pos, side, owner, MESecurity.TERMINAL_ITEM.get());
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
     * A separate powered network whose storage bus faces {@code target}: bus, cable, then an energy cell one
     * block further out. The bus goes in before the cable so the new cable never joins the faced hardware.
     */
    private static StorageBusPart subnet(ServerLevel level, BlockPos target, Direction away, ServerPlayer placer) {
        var host = target.relative(away);
        var bus = PartHelper.setPart(level, host, away.getOpposite(), placer, AEParts.STORAGE_BUS.get());
        PartHelper.setPart(level, host, null, placer, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        level.setBlockAndUpdate(host.relative(away), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        if (bus == null) throw new IllegalStateException("Could not place the GameTest storage bus");
        return bus;
    }

    private static void clear(ServerLevel level, BlockPos target, Direction away) {
        level.setBlockAndUpdate(target.relative(away, 2), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(target.relative(away), Blocks.AIR.defaultBlockState());
    }

    private static MEStorage storage(StorageBusPart bus) {
        return bus.getGridNode().getGrid().getStorageService().getInventory();
    }

    private static long visible(StorageBusPart bus) {
        return storage(bus).getAvailableStacks().get(DIAMOND);
    }

    private static ItemStack filledCell() {
        var cell = AEItems.ITEM_CELL_1K.stack();
        var storage = StorageCells.getCellInventory(cell, null);
        storage.insert(DIAMOND, 12, Actionable.MODULATE, IActionSource.empty());
        storage.persist();
        return cell;
    }

    /** A1: an Interface hands its whole network to any facing storage bus. */
    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 200)
    public static void subnetStorageBusNeedsBuildOnProtectedInterface(GameTestHelper helper) {
        var level = helper.getLevel();
        var hub = helper.absolutePos(CENTER.west());
        var face = helper.absolutePos(CENTER);
        var owner = player(level, "SubnetOwner", face);
        var builder = player(level, "SubnetBuilder", face);
        var outsider = player(level, "SubnetOutsider", face);
        var terminal = terminal(level, hub, Direction.WEST, owner);
        level.setBlockAndUpdate(face, AEBlocks.INTERFACE.block().defaultBlockState());
        helper.runAfterDelay(20, () -> {
            trust(level, terminal, owner, builder, Permission.BUILD.bit());
            IGrid grid = terminal.getGridNode().getGrid();
            var stock = new TestStorage();
            grid.getStorageService().addGlobalStorageProvider(mounts -> mounts.mount(stock));
            var network = grid.getStorageService().getInventory();
            network.insert(DIAMOND, 20, Actionable.MODULATE, IActionSource.ofMachine(terminal));
            var foreign = subnet(level, face, Direction.EAST, outsider);
            var owned = subnet(level, face, Direction.UP, owner);
            var built = subnet(level, face, Direction.SOUTH, builder);
            // Storage buses first look at their target within 60 ticks of joining a grid.
            helper.runAfterDelay(100, () -> {
                helper.assertTrue(owned.getGridNode().getGrid() != grid && foreign.getGridNode().getGrid() != grid,
                        "the subnets must stay separate grids");
                helper.assertTrue(owned.getInternalHandler() == network, "the owner's bus mounts the Interface's network");
                helper.assertTrue(built.getInternalHandler() == network, "a Build player's bus mounts it too");
                helper.assertTrue(foreign.getInternalHandler() != network, "an outsider's bus must not mount the protected network");
                helper.assertValueEqual(20L, visible(owned), "the owner's subnet sees the protected stock");
                helper.assertValueEqual(0L, visible(foreign), "an outsider's subnet sees nothing");
                helper.assertValueEqual(1L, storage(owned).extract(DIAMOND, 1, Actionable.SIMULATE, IActionSource.ofMachine(owned)),
                        "the owner's subnet machines may extract");
                helper.assertValueEqual(0L, storage(foreign).extract(DIAMOND, 1, Actionable.MODULATE, IActionSource.ofMachine(foreign)),
                        "the outsider's subnet machines extract nothing");

                // The live rule also covers a mount that predates protection or a revocation.
                helper.assertValueEqual(0L, network.extract(DIAMOND, 1, Actionable.MODULATE, IActionSource.ofMachine(foreign)),
                        "a machine placed by an outsider on another grid cannot extract");
                helper.assertValueEqual(0L, network.insert(DIAMOND, 1, Actionable.MODULATE, IActionSource.ofMachine(foreign)),
                        "nor insert");
                helper.assertValueEqual(1L, network.extract(DIAMOND, 1, Actionable.SIMULATE, IActionSource.ofMachine(built)),
                        "a Build player's machine may reach in");
                helper.assertValueEqual(1L, network.extract(DIAMOND, 1, Actionable.SIMULATE, IActionSource.ofMachine(terminal)),
                        "machines on the protected grid itself are unrestricted");
                helper.assertValueEqual(1L, network.extract(DIAMOND, 1, Actionable.SIMULATE, IActionSource.empty()),
                        "sources without a machine keep AE2's behavior");
                trust(level, terminal, owner, builder, 0);
                helper.assertValueEqual(0L, storage(built).extract(DIAMOND, 1, Actionable.MODULATE, IActionSource.ofMachine(built)),
                        "revoking Build stops an already mounted subnet immediately");
                helper.assertValueEqual(20L, stock.amount(DIAMOND), "denied transfers move nothing");

                for (var away : new Direction[] {Direction.EAST, Direction.UP, Direction.SOUTH}) clear(level, face, away);
                level.setBlockAndUpdate(face, Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(hub, Blocks.AIR.defaultBlockState());
                helper.succeed();
            });
        });
    }

    /** A1: an ME Chest's cell never passes through network storage, so only the mount rule protects it. */
    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 200)
    public static void subnetStorageBusNeedsBuildOnProtectedChest(GameTestHelper helper) {
        var level = helper.getLevel();
        var chestPos = helper.absolutePos(CENTER);
        var owner = player(level, "ChestSubnetOwner", chestPos);
        var outsider = player(level, "ChestSubnetOutsider", chestPos);
        level.setBlockAndUpdate(chestPos, AEBlocks.ME_CHEST.block().defaultBlockState());
        var chest = (MEChestBlockEntity) level.getBlockEntity(chestPos);
        var terminal = terminal(level, chestPos.below(), Direction.NORTH, owner);
        // The chest offers no ME storage on its front, so use the two faces across its other horizontal axis.
        var first = chest.getFront().getAxis() == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(SecurityAccess.grid(chest) == terminal.getGridNode().getGrid(),
                    "the chest must have joined the protected grid");
            chest.getInternalInventory().setItemDirect(1, filledCell());
            var owned = subnet(level, chestPos, first, owner);
            var foreign = subnet(level, chestPos, first.getOpposite(), outsider);
            helper.runAfterDelay(100, () -> {
                helper.assertTrue(owned.getInternalHandler() instanceof SupplierStorage, "the owner's bus mounts the chest's cell");
                helper.assertFalse(foreign.getInternalHandler() instanceof SupplierStorage, "an outsider's bus must not mount the cell");
                helper.assertValueEqual(12L, visible(owned), "the owner's subnet sees the cell contents");
                helper.assertValueEqual(0L, visible(foreign), "an outsider's subnet sees nothing");
                helper.assertValueEqual(0L, storage(foreign).extract(DIAMOND, 1, Actionable.MODULATE, IActionSource.ofMachine(foreign)),
                        "and extracts nothing");
                clear(level, chestPos, first);
                clear(level, chestPos, first.getOpposite());
                chest.getInternalInventory().setItemDirect(1, ItemStack.EMPTY);
                level.setBlockAndUpdate(chestPos, Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(chestPos.below(), Blocks.AIR.defaultBlockState());
                helper.succeed();
            });
        });
    }

    /** A2: the CPU screen opens with View, but its scheduling mode is hardware configuration. */
    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void cpuSchedulingModeRequiresBuild(GameTestHelper helper) {
        var level = helper.getLevel();
        var hub = helper.absolutePos(CENTER);
        var owner = player(level, "CpuModeOwner", hub);
        var viewer = player(level, "CpuModeViewer", hub);
        var builder = player(level, "CpuModeBuilder", hub);
        var terminal = terminal(level, hub, Direction.UP, owner);
        level.setBlockAndUpdate(hub.east(), AEBlocks.CRAFTING_STORAGE_1K.block().defaultBlockState());
        helper.runAfterDelay(30, () -> {
            trust(level, terminal, owner, viewer, 0);
            trust(level, terminal, owner, builder, Permission.BUILD.bit());
            var cpu = (CraftingBlockEntity) level.getBlockEntity(hub.east());
            helper.assertTrue(cpu.getCluster() != null, "the crafting CPU must have formed");
            var packet = new ConfigButtonPacket(Settings.CPU_SELECTION_MODE, false);
            try {
                viewer.containerMenu = new CraftingCPUMenu(CraftingCPUMenu.TYPE, 7, viewer.getInventory(), cpu);
                helper.assertTrue(viewer.containerMenu.stillValid(viewer), "trust alone still opens the CPU status screen");
                packet.handleOnServer(viewer);
                helper.assertTrue(cpu.getCluster().getSelectionMode() == CpuSelectionMode.ANY,
                        "a View-only player cannot change the scheduling mode");
                builder.containerMenu = new CraftingCPUMenu(CraftingCPUMenu.TYPE, 8, builder.getInventory(), cpu);
                packet.handleOnServer(builder);
                helper.assertTrue(cpu.getCluster().getSelectionMode() != CpuSelectionMode.ANY,
                        "a Build player can change the scheduling mode");
            } finally {
                viewer.containerMenu.removed(viewer);
                builder.containerMenu.removed(builder);
                viewer.containerMenu = viewer.inventoryMenu;
                builder.containerMenu = builder.inventoryMenu;
            }
            level.setBlockAndUpdate(hub.east(), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(hub, Blocks.AIR.defaultBlockState());
            helper.succeed();
        });
    }

    /** A6(a): a product consumed by the player's own later job is no longer collectible. */
    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void ownCraftingIngredientsSpendAllowance(GameTestHelper helper) {
        var level = helper.getLevel();
        var hub = helper.absolutePos(CENTER);
        var owner = player(level, "IngredientOwner", hub);
        var crafter = player(level, "IngredientCrafter", hub);
        var terminal = terminal(level, hub, Direction.NORTH, owner);
        var storageTerminal = PartHelper.setPart(level, hub, Direction.SOUTH, owner, AEParts.TERMINAL.get());
        helper.runAfterDelay(20, () -> {
            trust(level, terminal, owner, crafter, Permission.CRAFT.bit());
            var data = SecuritySavedData.get(level.getServer());
            var id = terminal.terminalId();
            var grid = terminal.getGridNode().getGrid();
            var stock = new TestStorage();
            grid.getStorageService().addGlobalStorageProvider(mounts -> mounts.mount(stock));
            var network = grid.getStorageService().getInventory();
            network.insert(DIAMOND, 20, Actionable.MODULATE, IActionSource.ofMachine(terminal));
            var normal = IActionSource.ofPlayer(crafter);
            var job = CraftingActionSource.approved(normal, grid);

            data.recordCraftedOutput(id, crafter.getUUID(), new GenericStack(DIAMOND, 4));
            helper.assertValueEqual(3L, network.extract(DIAMOND, 3, Actionable.SIMULATE, job), "ingredient simulation");
            helper.assertValueEqual(4L, data.craftedAmount(id, crafter.getUUID(), DIAMOND), "simulation spends nothing");
            helper.assertValueEqual(3L, network.extract(DIAMOND, 3, Actionable.MODULATE, job), "the job takes its ingredients");
            helper.assertValueEqual(1L, data.craftedAmount(id, crafter.getUUID(), DIAMOND),
                    "ingredients taken by the player's own job spend the allowance");
            helper.assertValueEqual(1L, network.extract(DIAMOND, 64, Actionable.MODULATE, normal), "only the remainder is collectible");
            helper.assertValueEqual(5L, network.extract(DIAMOND, 5, Actionable.MODULATE, job),
                    "ingredient extraction itself is never limited by the allowance");
            helper.assertValueEqual(0L, data.craftedAmount(id, crafter.getUUID(), DIAMOND), "and never drives it below zero");

            // A menu never forwards the internal source, and still charges a nested network extraction once.
            data.recordCraftedOutput(id, crafter.getUUID(), new GenericStack(DIAMOND, 4));
            var menuStorage = new PlayerMenuStorage(storageTerminal.getInventory(), storageTerminal, crafter);
            helper.assertValueEqual(1L, menuStorage.extract(DIAMOND, 1, Actionable.MODULATE, job), "menu collection");
            helper.assertValueEqual(3L, data.craftedAmount(id, crafter.getUUID(), DIAMOND), "menu collection is charged once");
            helper.assertValueEqual(3L, menuStorage.extract(DIAMOND, 64, Actionable.MODULATE, normal), "menu collection stays capped");
            helper.assertValueEqual(7L, stock.amount(DIAMOND), "exactly the granted amounts left the network");
            level.setBlockAndUpdate(hub, Blocks.AIR.defaultBlockState());
            helper.succeed();
        });
    }

    /** A5 and A7: personal wireless item menus need only View; the Network Tool status screen is owner-only. */
    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void wirelessItemMenusAndNetworkToolStatus(GameTestHelper helper) {
        var level = helper.getLevel();
        var hub = helper.absolutePos(CENTER);
        var owner = player(level, "StatusOwner", hub);
        var viewer = player(level, "StatusViewer", hub);
        var builder = player(level, "StatusBuilder", hub);
        var outsider = player(level, "StatusOutsider", hub);
        var terminal = terminal(level, hub, Direction.NORTH, owner);
        var storageTerminal = PartHelper.setPart(level, hub, Direction.SOUTH, owner, AEParts.TERMINAL.get());
        helper.runAfterDelay(20, () -> {
            trust(level, terminal, owner, viewer, 0);
            trust(level, terminal, owner, builder, Permission.BUILD.bit());
            // Menu tags only hold registered types, so the ae2wtlib classification is live only with it installed.
            if (net.neoforged.fml.ModList.get().isLoaded("ae2wtlib")) {
                for (var name : new String[] {"trash", "magnet", "wireless_crafting_terminal"}) {
                    var type = ResourceLocation.fromNamespaceAndPath("ae2wtlib", name);
                    helper.assertTrue(SecurityAccess.opening(viewer, storageTerminal, type), name + " needs only View");
                    helper.assertFalse(SecurityAccess.opening(outsider, storageTerminal, type), name + " still needs trust");
                }
                for (var name : new String[] {"wireless_pattern_access_terminal", "wireless_pattern_encoding_terminal"}) {
                    helper.assertFalse(SecurityAccess.opening(builder, storageTerminal,
                            ResourceLocation.fromNamespaceAndPath("ae2wtlib", name)), name + " stays owner-only");
                }
            }
            helper.assertFalse(SecurityAccess.opening(viewer, storageTerminal,
                    ResourceLocation.fromNamespaceAndPath("ae2wtlib", "wireless_pattern_access_terminal")),
                    "wireless pattern management stays owner-only");

            var grid = terminal.getGridNode().getGrid();
            var status = ResourceLocation.fromNamespaceAndPath("ae2", "networkstatus");
            for (var user : new ServerPlayer[] {owner, builder, outsider}) {
                user.setItemInHand(InteractionHand.MAIN_HAND, AEItems.NETWORK_TOOL.stack());
            }
            var ownerHost = toolHost(owner, hub);
            helper.assertTrue(SecurityAccess.grid(ownerHost) == grid, "the Network Tool host resolves the clicked network");
            helper.assertTrue(SecurityAccess.opening(owner, ownerHost, status), "the owner may read network status");
            helper.assertFalse(SecurityAccess.opening(builder, toolHost(builder, hub), status),
                    "network status is owner-only, as on the controller");
            helper.assertFalse(SecurityAccess.opening(outsider, toolHost(outsider, hub), status), "outsiders are refused");
            var toolbox = new NetworkToolMenuHost<>(AEItems.NETWORK_TOOL.asItem(), outsider,
                    MenuLocators.forHand(outsider, InteractionHand.MAIN_HAND), null);
            helper.assertTrue(SecurityAccess.opening(outsider, toolbox, ResourceLocation.fromNamespaceAndPath("ae2", "networktool")),
                    "the tool's own toolbox is not tied to a network");
            for (var user : new ServerPlayer[] {owner, builder, outsider}) {
                user.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
            level.setBlockAndUpdate(hub, Blocks.AIR.defaultBlockState());
            helper.succeed();
        });
    }

    private static NetworkToolMenuHost<?> toolHost(ServerPlayer player, BlockPos clicked) {
        return new NetworkToolMenuHost<>(AEItems.NETWORK_TOOL.asItem(), player,
                MenuLocators.forHand(player, InteractionHand.MAIN_HAND), GridHelper.getNodeHost(player.level(), clicked));
    }

    private static final class TestStorage implements MEStorage {
        private final Map<AEKey, Long> amounts = new HashMap<>();

        long amount(AEKey key) {
            return amounts.getOrDefault(key, 0L);
        }

        @Override
        public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            if (mode == Actionable.MODULATE) amounts.merge(key, amount, Long::sum);
            return amount;
        }

        @Override
        public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            long extracted = Math.min(amount, amount(key));
            if (mode == Actionable.MODULATE && extracted > 0) amounts.merge(key, -extracted, Long::sum);
            return extracted;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            amounts.forEach(out::add);
        }

        @Override
        public Component getDescription() {
            return Component.literal("ME Security access GameTest storage");
        }
    }
}
