package dev.ae2security.gametest;

import java.util.*;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import appeng.api.util.AEColor;
import appeng.block.crafting.AbstractCraftingUnitBlock;
import appeng.blockentity.AEBaseInvBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.menu.implementations.DriveMenu;
import dev.ae2security.MESecurity;
import dev.ae2security.part.SecurityTerminalPart;
import dev.ae2security.security.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/** Exercises native mutation boundaries and conservation, rather than only the policy helpers. */
@GameTestHolder(MESecurity.ID)
@PrefixGameTestTemplate(false)
public final class HardwareGameTests {
    private static final BlockPos CENTER = new BlockPos(2, 2, 2);

    private static ServerPlayer player(ServerLevel level, String name, BlockPos pos) {
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), name));
        player.setPos(Vec3.atCenterOf(pos));
        return player;
    }

    private static SecurityTerminalPart terminal(ServerLevel level, BlockPos pos, ServerPlayer owner) {
        PartHelper.setPart(level, pos, null, owner, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        return PartHelper.setPart(level, pos, Direction.UP, owner, MESecurity.TERMINAL_ITEM.get());
    }

    private static InteractionResult place(ServerLevel level, ServerPlayer player, ItemStack stack, BlockPos against, Direction face) {
        var hit = new BlockHitResult(Vec3.atCenterOf(against).add(face.getStepX() * .49,
                face.getStepY() * .49, face.getStepZ() * .49), face, against, false);
        return stack.getItem().useOn(new UseOnContext(level, player, InteractionHand.MAIN_HAND, stack, hit));
    }

    private static ItemStack filledCell() {
        var cell = AEItems.ITEM_CELL_1K.stack();
        var storage = StorageCells.getCellInventory(cell, null);
        storage.insert(AEItemKey.of(Items.DIAMOND), 12, Actionable.MODULATE, IActionSource.empty());
        storage.persist();
        return cell;
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void terminalViewCellsRequireBuild(GameTestHelper helper) {
        var level = helper.getLevel();
        var center = helper.absolutePos(CENTER);
        var owner = player(level, "FilterOwner", center);
        var guest = player(level, "FilterGuest", center);
        var terminal = terminal(level, center, owner);
        var storageTerminal = PartHelper.setPart(level, center, Direction.SOUTH, owner, AEParts.TERMINAL.get());
        helper.runAfterDelay(20, () -> {
            var data = SecuritySavedData.get(level.getServer());
            var id = terminal.terminalId();
            data.update(id, data.policy(id).trust(owner.getUUID(), guest.getUUID(), Permission.VIEW.bit()));
            var cell = AEItems.VIEW_CELL.stack();
            storageTerminal.getViewCellStorage().setItemDirect(0, cell);
            var menu = new appeng.menu.me.common.MEStorageMenu(appeng.menu.me.common.MEStorageMenu.TYPE,
                    92, guest.getInventory(), storageTerminal);
            guest.containerMenu = menu;
            var slot = menu.getSlots(appeng.menu.SlotSemantics.VIEW_CELL).iterator().next();
            menu.clicked(slot.index, 0, ClickType.PICKUP, guest);
            helper.assertTrue(menu.getCarried().isEmpty() && storageTerminal.getViewCellStorage().getStackInSlot(0) == cell,
                    "View cannot remove a physical filter cell from shared terminal hardware");
            menu.quickMoveStack(guest, slot.index);
            helper.assertValueEqual(0, guest.getInventory().countItem(cell.getItem()), "View cannot quick-move terminal filter cells");
            data.update(id, data.policy(id).trust(owner.getUUID(), guest.getUUID(), Permission.BUILD.bit()));
            menu.clicked(slot.index, 0, ClickType.PICKUP, guest);
            helper.assertTrue(menu.getCarried().is(cell.getItem()), "Build permits removing the terminal filter cell");
            menu.clicked(slot.index, 0, ClickType.PICKUP, guest);
            helper.assertTrue(menu.getCarried().isEmpty(), "Build permits replacing the terminal filter cell");
            data.update(id, data.policy(id).trust(owner.getUUID(), guest.getUUID(), Permission.VIEW.bit()));
            menu.clicked(slot.index, 0, ClickType.PICKUP, guest);
            helper.assertTrue(menu.getCarried().isEmpty(), "filter-cell slots recheck revoked Build");
            guest.containerMenu = guest.inventoryMenu;
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void terminalBlockEntityReloadPreservesOwnership(GameTestHelper helper) {
        var level = helper.getLevel();
        var center = helper.absolutePos(CENTER);
        var owner = player(level, "ReloadOwner", center);
        var guest = player(level, "ReloadGuest", center);
        var terminal = terminal(level, center, owner);
        level.setBlockAndUpdate(center.east(), AEBlocks.DRIVE.block().defaultBlockState());
        helper.runAfterDelay(20, () -> {
            var data = SecuritySavedData.get(level.getServer());
            var id = terminal.terminalId();
            data.update(id, data.policy(id).trust(owner.getUUID(), guest.getUUID(), Permission.EXTRACT.bit()));
            var host = terminal.getBlockEntity();
            var state = host.getBlockState();
            var saved = host.saveWithFullMetadata(level.registryAccess());
            level.removeBlockEntity(center);
            helper.assertFalse(data.retired(id), "unloading a terminal is not removal");
            var restored = BlockEntity.loadStatic(center, state, saved, level.registryAccess());
            helper.assertTrue(restored != null, "serialized cable bus must load");
            level.setBlockEntity(restored);
            helper.runAfterDelay(10, () -> {
                var reloaded = (SecurityTerminalPart) PartHelper.getPart(level, center, Direction.UP);
                helper.assertValueEqual(id, reloaded.terminalId(), "reload preserves terminal identity");
                helper.assertTrue(data.policy(id).isOwner(owner.getUUID()), "reload preserves owner");
                helper.assertTrue(data.policy(id).allows(guest.getUUID(), Permission.EXTRACT), "reload preserves trusted permissions");
                helper.assertFalse(guest.gameMode.destroyBlock(center.east()), "reloaded grid still protects ordinary hardware");
                helper.assertTrue(reloaded.createMenu(93, owner.getInventory(), owner) != null, "owner can reconnect to management");
                var hit = new BlockHitResult(Vec3.atCenterOf(center).add(0, .49, 0), Direction.UP, center, false);
                ((appeng.blockentity.networking.CableBusBlockEntity) restored).disassembleWithWrench(owner, level, hit, ItemStack.EMPTY);
                helper.assertTrue(data.retired(id), "physical removal after reload retires the original policy");
                var replacement = PartHelper.setPart(level, center, Direction.UP, guest, MESecurity.TERMINAL_ITEM.get());
                helper.assertFalse(replacement.terminalId().equals(id), "replacement terminal receives a fresh identity");
                helper.assertTrue(data.policy(replacement.terminalId()).isOwner(guest.getUUID()), "replacement binds to its placer");
                helper.succeed();
            });
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void nativeHardwareAndRevokedDriveActions(GameTestHelper helper) {
        var level = helper.getLevel();
        var center = helper.absolutePos(CENTER);
        var owner = player(level, "NativeOwner", center);
        var guest = player(level, "NativeGuest", center);
        var terminal = terminal(level, center, owner);
        var positions = List.of(center.east(), center.west(), center.north(), center.south());
        var blocks = List.of(AEBlocks.DRIVE.block(), AEBlocks.INTERFACE.block(),
                AEBlocks.CONTROLLER.block(), AEBlocks.WIRELESS_ACCESS_POINT.block());
        for (int i = 0; i < blocks.size(); i++) level.setBlockAndUpdate(positions.get(i), blocks.get(i).defaultBlockState());
        var wireless = level.getBlockState(center.south());
        level.setBlockAndUpdate(center.south(), appeng.api.orientation.IOrientationStrategy.get(wireless)
                .setOrientation(wireless, Direction.SOUTH, 0));
        helper.runAfterDelay(20, () -> {
            var data = SecuritySavedData.get(level.getServer());
            var id = terminal.terminalId();
            data.update(id, data.policy(id).trust(owner.getUUID(), guest.getUUID(),
                    Permission.INSERT.bit() | Permission.EXTRACT.bit() | Permission.CRAFT.bit()));
            guest.setItemInHand(InteractionHand.MAIN_HAND, AEItems.CERTUS_QUARTZ_WRENCH.stack());
            for (var pos : positions) {
                var host = (appeng.blockentity.AEBaseBlockEntity) level.getBlockEntity(pos);
                helper.assertTrue(SecurityHardware.protectedBlock(level, pos), "fixture hardware must belong to a protected grid: " + host.getBlockState());
                helper.assertFalse(guest.gameMode.destroyBlock(pos), "network permissions cannot mine ordinary hardware");
                var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
                guest.setShiftKeyDown(true);
                helper.assertValueEqual(InteractionResult.FAIL,
                        appeng.hooks.WrenchHook.onPlayerUseBlock(guest, level, InteractionHand.MAIN_HAND, hit),
                        "network permissions cannot dismantle ordinary hardware");
                var state = level.getBlockState(pos);
                guest.setShiftKeyDown(false);
                appeng.hooks.WrenchHook.onPlayerUseBlock(guest, level, InteractionHand.MAIN_HAND, hit);
                helper.assertTrue(level.getBlockState(pos) == state, "unauthorized rotation cannot mutate hardware");
            }
            var drive = (DriveBlockEntity) level.getBlockEntity(center.east());
            var cell = filledCell();
            drive.getInternalInventory().setItemDirect(0, cell);
            data.update(id, data.policy(id).trust(owner.getUUID(), guest.getUUID(), Permission.BUILD.bit()));
            var menu = new DriveMenu(91, guest.getInventory(), drive);
            guest.containerMenu = menu;
            data.update(id, data.policy(id).trust(owner.getUUID(), guest.getUUID(), Permission.EXTRACT.bit()));
            for (var mode : List.of(ClickType.PICKUP, ClickType.QUICK_MOVE, ClickType.SWAP, ClickType.THROW, ClickType.PICKUP_ALL)) {
                menu.clicked(0, 0, mode, guest);
                helper.assertTrue(menu.getCarried().isEmpty()
                                && ItemStack.isSameItemSameComponents(cell, drive.getInternalInventory().getStackInSlot(0)),
                        "revoked Build must reject " + mode + " on an already open drive");
            }
            menu.quickMoveStack(guest, 0);
            var hotbar = menu.getSlots(appeng.menu.SlotSemantics.PLAYER_HOTBAR).iterator().next();
            menu.swapSlotContents(0, hotbar.index);
            helper.assertValueEqual(0, guest.getInventory().countItem(cell.getItem()), "direct quick-move and swaps cannot steal cells");
            menu.setCarried(cell.copy());
            menu.clicked(-999, 0, ClickType.QUICK_CRAFT, guest);
            menu.clicked(1, 1, ClickType.QUICK_CRAFT, guest);
            menu.clicked(-999, 2, ClickType.QUICK_CRAFT, guest);
            helper.assertTrue(menu.getCarried().getCount() == 1 && drive.getInternalInventory().getStackInSlot(1).isEmpty(),
                    "revoked Build must reject drag insertion without losing the carried cell");
            menu.setCarried(ItemStack.EMPTY);
            guest.containerMenu = guest.inventoryMenu;
            data.update(id, data.policy(id).trust(owner.getUUID(), guest.getUUID(), Permission.BUILD.bit()));
            helper.assertTrue(guest.gameMode.destroyBlock(center.north()), "Build allows mining a controller");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void wirelessCollectionAndShortcutRange(GameTestHelper helper) {
        var level = helper.getLevel();
        var center = helper.absolutePos(CENTER);
        var owner = player(level, "WirelessOwner", center);
        var guest = player(level, "WirelessGuest", center);
        var terminal = terminal(level, center, owner);
        var wireless = AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState();
        level.setBlockAndUpdate(center.south(), appeng.api.orientation.IOrientationStrategy.get(wireless)
                .setOrientation(wireless, Direction.SOUTH, 0));
        level.setBlockAndUpdate(center.east(), AEBlocks.DRIVE.block().defaultBlockState());
        level.setBlockAndUpdate(center.below(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        helper.runAfterDelay(20, () -> {
            ((DriveBlockEntity) level.getBlockEntity(center.east())).getInternalInventory().setItemDirect(0, filledCell());
            var data = SecuritySavedData.get(level.getServer());
            var id = terminal.terminalId();
            data.update(id, data.policy(id).trust(owner.getUUID(), guest.getUUID(), Permission.CRAFT.bit()));
            var stack = AEItems.WIRELESS_TERMINAL.stack();
            var item = (appeng.items.tools.powered.WirelessTerminalItem) stack.getItem();
            item.injectAEPower(stack, 10000, Actionable.MODULATE);
            appeng.items.tools.powered.WirelessTerminalItem.LINKABLE_HANDLER.link(stack,
                    net.minecraft.core.GlobalPos.of(level.dimension(), center.south()));
            guest.getInventory().setItem(0, stack);
            var host = item.getMenuHost(guest, appeng.menu.locator.MenuLocators.forInventorySlot(0), null);
            helper.assertTrue(host.getLinkStatus().connected(), "wireless fixture must have a powered, paired access point in range");
            var storage = new PlayerMenuStorage(host.getInventory(), host, guest);
            var diamond = AEItemKey.of(Items.DIAMOND);
            var source = IActionSource.ofPlayer(guest);
            helper.assertValueEqual(0L, storage.extract(diamond, 1, Actionable.MODULATE, source),
                    "pairing and Craft do not grant general extraction");
            data.recordCraftedOutput(id, guest.getUUID(), new appeng.api.stacks.GenericStack(diamond, 3));
            helper.assertValueEqual(1L, storage.extract(diamond, 1, Actionable.MODULATE, source), "wireless permits owned craft output");
            helper.assertValueEqual(2L, data.craftedAmount(id, guest.getUUID(), diamond), "wireless partial collection charges exactly once");
            data.update(id, data.policy(id).remove(owner.getUUID(), guest.getUUID()));
            helper.assertValueEqual(0L, storage.extract(diamond, 1, Actionable.MODULATE, source), "wireless rechecks revoked trust");

            if (net.neoforged.fml.ModList.get().isLoaded("ae2wtlib")) {
                stack = AEItems.WIRELESS_CRAFTING_TERMINAL.stack();
                item = (appeng.items.tools.powered.WirelessTerminalItem) stack.getItem();
                item.injectAEPower(stack, 10000, Actionable.MODULATE);
                appeng.items.tools.powered.WirelessTerminalItem.LINKABLE_HANDLER.link(stack,
                        net.minecraft.core.GlobalPos.of(level.dimension(), center.south()));
                guest.getInventory().setItem(0, stack);
                guest.getInventory().selected = 1;
                guest.getInventory().setItem(1, new ItemStack(Items.IRON_INGOT));
                data.update(id, data.policy(id).trust(owner.getUUID(), guest.getUUID(),
                        Permission.INSERT.bit() | Permission.EXTRACT.bit()));
                try {
                    var components = Class.forName("de.mari_023.ae2wtlib.api.AE2wtlibComponents");
                    @SuppressWarnings("unchecked")
                    var pickBlock = (net.minecraft.core.component.DataComponentType<Boolean>) components.getField("PICK_BLOCK").get(null);
                    stack.set(pickBlock, true);
                    var handlerClass = Class.forName("de.mari_023.ae2wtlib.wct.CraftingTerminalHandler");
                    var handler = handlerClass.getMethod("getCraftingTerminalHandler", net.minecraft.world.entity.player.Player.class)
                            .invoke(null, guest);
                    helper.assertTrue((Boolean) handlerClass.getMethod("inRange").invoke(handler), "optional wireless shortcut starts in range");
                    var pick = Class.forName("de.mari_023.ae2wtlib.AE2wtlibEvents")
                            .getMethod("pickBlock", ServerPlayer.class, ItemStack.class);
                    guest.setPos(Vec3.atCenterOf(center.east(128)));
                    pick.invoke(null, guest, new ItemStack(Items.DIAMOND));
                    helper.assertValueEqual(0, guest.getInventory().countItem(Items.DIAMOND), "cached wireless shortcuts must recheck range");
                    helper.assertValueEqual(1, guest.getInventory().countItem(Items.IRON_INGOT), "out-of-range shortcut preserves the replaced hotbar item");
                    guest.setPos(Vec3.atCenterOf(center));
                    pick.invoke(null, guest, new ItemStack(Items.DIAMOND));
                    helper.assertTrue(guest.getInventory().countItem(Items.DIAMOND) > 0, "nearby optional wireless shortcut still works");
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError("Could not exercise optional wireless shortcut", e);
                }
            }
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void p2pAttunementRequiresBuild(GameTestHelper helper) {
        var level = helper.getLevel();
        var center = helper.absolutePos(CENTER);
        var owner = player(level, "AttunementOwner", center);
        var guest = player(level, "AttunementGuest", center);
        var terminal = terminal(level, center, owner);
        var tunnel = PartHelper.setPart(level, center, Direction.SOUTH, owner, AEParts.ME_P2P_TUNNEL.get());
        helper.runAfterDelay(20, () -> {
            var trigger = new ItemStack(Items.CHEST);
            var replacement = appeng.api.features.P2PTunnelAttunement.getTunnelPartByTriggerItem(trigger);
            helper.assertTrue(replacement.is(AEParts.ITEM_P2P_TUNNEL.get()), "fixture must attune an item tunnel");
            tunnel.onUseItemOn(trigger, guest, InteractionHand.MAIN_HAND, Vec3.ZERO);
            helper.assertTrue(tunnel.getHost().getPart(Direction.SOUTH) == tunnel,
                    "ordinary attunement items cannot replace a protected P2P part without Build");
            helper.assertValueEqual(1, trigger.getCount(), "denied attunement conserves the held item");
            var data = SecuritySavedData.get(level.getServer());
            data.update(terminal.terminalId(), data.policy(terminal.terminalId()).trust(owner.getUUID(),
                    guest.getUUID(), Permission.BUILD.bit()));
            tunnel.onUseItemOn(trigger, guest, InteractionHand.MAIN_HAND, Vec3.ZERO);
            helper.assertTrue(PartHelper.getPart(level, center, Direction.SOUTH).getPartItem() == AEParts.ITEM_P2P_TUNNEL.get(),
                    "Build allows native P2P attunement");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void p2pAttunementChecksNewOutwardConnection(GameTestHelper helper) {
        var level = helper.getLevel();
        var center = helper.absolutePos(CENTER);
        var owner = player(level, "OutwardOwner", center);
        var guest = player(level, "OutwardGuest", center);
        var terminal = terminal(level, center, owner);
        PartHelper.setPart(level, center.east(), null, guest, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        var tunnel = PartHelper.setPart(level, center.east(), Direction.WEST, guest, AEParts.ITEM_P2P_TUNNEL.get());
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(tunnel.getGridNode().getGrid() != terminal.getGridNode().getGrid(),
                    "item tunnel fixture starts on a public transport grid");
            var trigger = AEParts.GLASS_CABLE.stack(AEColor.TRANSPARENT, 1);
            helper.assertTrue(appeng.api.features.P2PTunnelAttunement.getTunnelPartByTriggerItem(trigger)
                    .is(AEParts.ME_P2P_TUNNEL.get()), "fixture must attune an ME tunnel");
            tunnel.onUseItemOn(trigger, guest, InteractionHand.MAIN_HAND, Vec3.ZERO);
            helper.assertTrue(PartHelper.getPart(level, center.east(), Direction.WEST) == tunnel,
                    "attunement must authorize the new outward grid before replacing the public tunnel");
            tunnel.onUseItemOn(trigger, owner, InteractionHand.MAIN_HAND, Vec3.ZERO);
            helper.assertTrue(PartHelper.getPart(level, center.east(), Direction.WEST).getPartItem() == AEParts.ME_P2P_TUNNEL.get(),
                    "owner of the outward grid may attune the public tunnel");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void monitorConfigurationPreservesPermittedTransfers(GameTestHelper helper) {
        var level = helper.getLevel();
        var center = helper.absolutePos(CENTER);
        var owner = player(level, "MonitorOwner", center);
        var guest = player(level, "MonitorGuest", center);
        var terminal = terminal(level, center, owner);
        var monitor = PartHelper.setPart(level, center, Direction.SOUTH, owner, AEParts.CONVERSION_MONITOR.get());
        level.setBlockAndUpdate(center.below(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(center.east(), AEBlocks.DRIVE.block().defaultBlockState());
        helper.runAfterDelay(20, () -> {
            var drive = (DriveBlockEntity) level.getBlockEntity(center.east());
            drive.getInternalInventory().setItemDirect(0, filledCell());
            helper.assertTrue(monitor.getMainNode().isActive(), "monitor fixture must be active");
            monitor.onUseItemOn(new ItemStack(Items.DIAMOND), guest, InteractionHand.MAIN_HAND, Vec3.ZERO);
            helper.assertTrue(monitor.getDisplayed() == null, "setting a monitor display requires Build");
            monitor.onUseItemOn(new ItemStack(Items.DIAMOND), owner, InteractionHand.MAIN_HAND, Vec3.ZERO);
            helper.assertValueEqual(AEItemKey.of(Items.DIAMOND), monitor.getDisplayed(), "owner may configure monitor");
            guest.setShiftKeyDown(true);
            monitor.onUseWithoutItem(guest, Vec3.ZERO);
            helper.assertFalse(monitor.isLocked(), "changing a monitor lock requires Build");
            var data = SecuritySavedData.get(level.getServer());
            data.update(terminal.terminalId(), data.policy(terminal.terminalId()).trust(owner.getUUID(),
                    guest.getUUID(), Permission.BUILD.bit()));
            monitor.onUseWithoutItem(guest, Vec3.ZERO);
            helper.assertTrue(monitor.isLocked(), "Build allows locking a monitor");
            guest.setShiftKeyDown(false);
            data.update(terminal.terminalId(), data.policy(terminal.terminalId()).trust(owner.getUUID(),
                    guest.getUUID(), Permission.INSERT.bit() | Permission.EXTRACT.bit()));
            var deposit = new ItemStack(Items.DIAMOND, 4);
            monitor.onUseItemOn(deposit, guest, InteractionHand.MAIN_HAND, Vec3.ZERO);
            helper.assertTrue(deposit.isEmpty(), "Insert without Build permits conversion-monitor deposits");
            monitor.onShiftClicked(guest, Vec3.ZERO);
            helper.assertValueEqual(1, guest.getInventory().countItem(Items.DIAMOND),
                    "Extract without Build permits conversion-monitor withdrawals");
            var hit = new BlockHitResult(Vec3.atCenterOf(center).add(0, 0, .49), Direction.SOUTH, center, false);
            guest.setPos(Vec3.atCenterOf(center.east(64)));
            new appeng.core.network.serverbound.PartLeftClickPacket(hit, true).handleOnServer(guest);
            helper.assertValueEqual(1, guest.getInventory().countItem(Items.DIAMOND),
                    "a forged distant part packet cannot bypass normal interaction range");
            guest.setPos(Vec3.atCenterOf(center));
            new appeng.core.network.serverbound.PartLeftClickPacket(hit, true).handleOnServer(guest);
            helper.assertValueEqual(2, guest.getInventory().countItem(Items.DIAMOND),
                    "nearby part packets retain permitted extraction");
            guest.setShiftKeyDown(true);
            monitor.onUseWithoutItem(guest, Vec3.ZERO);
            helper.assertTrue(monitor.isLocked(), "revoked Build cannot unlock a monitor");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void facadePlacementRequiresBuild(GameTestHelper helper) {
        var level = helper.getLevel();
        var center = helper.absolutePos(CENTER);
        var owner = player(level, "FacadeOwner", center);
        var guest = player(level, "FacadeGuest", center);
        var terminal = terminal(level, center, owner);
        helper.runAfterDelay(20, () -> {
            var facade = AEItems.FACADE.get().createFacadeForItem(new ItemStack(Blocks.STONE), false);
            helper.assertFalse(facade.isEmpty(), "fixture must contain a valid facade");
            var hit = new BlockHitResult(Vec3.atCenterOf(center).add(.1, 0, 0), Direction.EAST, center, false);
            guest.setItemInHand(InteractionHand.MAIN_HAND, facade);
            var denied = new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(
                    guest, InteractionHand.MAIN_HAND, center, hit);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(denied);
            if (!denied.isCanceled()) facade.getItem().onItemUseFirst(facade,
                    new UseOnContext(level, guest, InteractionHand.MAIN_HAND, facade, hit));
            helper.assertTrue(terminal.getHost().getFacadeContainer().getFacade(Direction.EAST) == null,
                    "a facade cannot reconfigure a protected cable without Build");
            helper.assertValueEqual(1, facade.getCount(), "denied facade placement conserves the item");
            var data = SecuritySavedData.get(level.getServer());
            data.update(terminal.terminalId(), data.policy(terminal.terminalId()).trust(owner.getUUID(),
                    guest.getUUID(), Permission.BUILD.bit()));
            var allowed = new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(
                    guest, InteractionHand.MAIN_HAND, center, hit);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(allowed);
            helper.assertFalse(allowed.isCanceled(), "Build allows the facade placement event");
            facade.getItem().onItemUseFirst(facade, new UseOnContext(level, guest, InteractionHand.MAIN_HAND, facade, hit));
            helper.assertTrue(terminal.getHost().getFacadeContainer().getFacade(Direction.EAST) != null,
                    "Build allows facade installation");
            helper.assertValueEqual(0, facade.getCount(), "successful facade placement consumes one item");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void conflictOwnerCanWrenchOwnTerminalThroughEvent(GameTestHelper helper) {
        var level = helper.getLevel();
        var center = helper.absolutePos(CENTER);
        var owner = player(level, "RecoveryOwner", center);
        var other = player(level, "RecoveryOther", center);
        var first = terminal(level, center, owner);
        PartHelper.setPart(level, center, Direction.SOUTH, other, MESecurity.TERMINAL_ITEM.get());
        helper.runAfterDelay(20, () -> {
            helper.assertValueEqual(2, first.getGridNode().getGrid().getService(ISecurityGrid.class).terminalIds().size(),
                    "fixture must create a terminal conflict");
            owner.setShiftKeyDown(true);
            owner.setItemInHand(InteractionHand.MAIN_HAND, AEItems.CERTUS_QUARTZ_WRENCH.stack());
            var hit = new BlockHitResult(Vec3.atCenterOf(center).add(0, .49, 0), Direction.UP, center, false);
            var event = new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(
                    owner, InteractionHand.MAIN_HAND, center, hit);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
            helper.assertTrue(PartHelper.getPart(level, center, Direction.UP) == null,
                    "owner must recover from conflict through the normal wrench event");
            helper.assertTrue(SecuritySavedData.get(level.getServer()).retired(first.terminalId()),
                    "normal recovery retires only the removed terminal");
            helper.assertTrue(PartHelper.getPart(level, center, Direction.SOUTH) != null,
                    "recovery preserves the other owner's terminal");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 120)
    public static void ordinaryDriveMiningWrenchAndMenus(GameTestHelper helper) {
        var level = helper.getLevel();
        var center = helper.absolutePos(CENTER);
        var drivePos = center.east();
        var owner = player(level, "HardwareOwner", center);
        var builder = player(level, "HardwareBuilder", center);
        var guest = player(level, "HardwareGuest", center);
        var terminal = terminal(level, center, owner);
        helper.runAfterDelay(20, () -> {
            var denied = new ItemStack(AEBlocks.DRIVE.block());
            helper.assertFalse(place(level, guest, denied, center, Direction.EAST).consumesAction(), "outsider placement must fail");
            helper.assertTrue(level.isEmptyBlock(drivePos) && denied.getCount() == 1, "denied placement conserves block and item");
            var data = SecuritySavedData.get(level.getServer());
            data.update(terminal.terminalId(), data.policy(terminal.terminalId()).trust(owner.getUUID(), builder.getUUID(), Permission.BUILD.bit()));
            helper.assertTrue(place(level, builder, new ItemStack(AEBlocks.DRIVE.block()), center, Direction.EAST).consumesAction(), "Build allows ordinary drive placement");
            helper.runAfterDelay(20, () -> {
                var drive = (DriveBlockEntity) level.getBlockEntity(drivePos);
                helper.assertFalse(drive.getMainNode().isPowered(), "test must establish protection without power");
                var cell = filledCell();
                drive.getInternalInventory().setItemDirect(0, cell.copy());
                data.update(terminal.terminalId(), data.policy(terminal.terminalId()).trust(owner.getUUID(), guest.getUUID(),
                        Permission.INSERT.bit() | Permission.EXTRACT.bit() | Permission.CRAFT.bit()));
                helper.assertFalse(guest.gameMode.destroyBlock(drivePos), "other permissions cannot mine a drive");
                guest.setItemInHand(InteractionHand.MAIN_HAND, AEItems.CERTUS_QUARTZ_WRENCH.stack());
                guest.setShiftKeyDown(true);
                var hit = new BlockHitResult(Vec3.atCenterOf(drivePos), Direction.WEST, drivePos, false);
                helper.assertValueEqual(InteractionResult.FAIL, appeng.hooks.WrenchHook.onPlayerUseBlock(guest, level,
                        InteractionHand.MAIN_HAND, hit), "native wrench path must reject outsiders");
                helper.assertTrue(ItemStack.isSameItemSameComponents(cell, drive.getInternalInventory().getStackInSlot(0)), "denied removal preserves cell contents");
                helper.assertFalse(SecurityAccess.opening(guest, drive, ResourceLocation.parse("ae2:drive")), "drive menu requires Build");
                helper.assertTrue(SecurityAccess.opening(builder, drive, ResourceLocation.parse("ae2:drive")), "Build permits drive menu without Extract");
                var menu = new DriveMenu(81, builder.getInventory(), drive);
                builder.containerMenu = menu;
                menu.clicked(0, 0, ClickType.PICKUP, builder);
                helper.assertTrue(ItemStack.isSameItemSameComponents(cell, menu.getCarried()), "Build may manually collect a filled cell");
                menu.clicked(0, 0, ClickType.PICKUP, builder);
                helper.assertTrue(menu.getCarried().isEmpty(), "manual replacement must conserve the cell");
                builder.setPos(Vec3.atCenterOf(drivePos).add(8.01, 0, 0));
                menu.clicked(0, 0, ClickType.PICKUP, builder);
                helper.assertTrue(menu.getCarried().isEmpty() && drive.getInternalInventory().getStackInSlot(0).getCount() == 1, "far-away clicks cannot move cells");
                builder.setPos(Vec3.atCenterOf(drivePos));
                builder.containerMenu = builder.inventoryMenu;
                builder.setItemInHand(InteractionHand.MAIN_HAND, AEItems.CERTUS_QUARTZ_WRENCH.stack());
                builder.setShiftKeyDown(true);
                helper.assertTrue(appeng.hooks.WrenchHook.onPlayerUseBlock(builder, level, InteractionHand.MAIN_HAND, hit).consumesAction(), "Build allows native dismantling");
                helper.assertTrue(level.isEmptyBlock(drivePos), "authorized dismantling removes drive");
                helper.assertValueEqual(1, builder.getInventory().countItem(cell.getItem()), "dismantling returns exactly one cell");
                var recovered = builder.getInventory().items.stream().filter(stack -> stack.is(cell.getItem())).findFirst().orElseThrow();
                helper.assertTrue(ItemStack.isSameItemSameComponents(cell, recovered), "recovered cell retains contents");
                helper.assertFalse(guest.gameMode.destroyBlock(center), "terminal-containing block remains owner-only");
                builder.containerMenu = builder.inventoryMenu;
                helper.succeed();
            });
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 120)
    public static void cachedCellHandlersFollowProtectionOnEveryFace(GameTestHelper helper) {
        var level = helper.getLevel();
        var center = helper.absolutePos(CENTER);
        var owner = player(level, "CellAutomationOwner", center);
        PartHelper.setPart(level, center, null, owner, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        var positions = List.of(center.east(), center.west(), center.south(), center.north());
        var blocks = List.of(AEBlocks.DRIVE.block(), AEBlocks.ME_CHEST.block(), AEBlocks.IO_PORT.block(), AEBlocks.SPATIAL_IO_PORT.block());
        for (int i = 0; i < blocks.size(); i++) level.setBlockAndUpdate(positions.get(i), blocks.get(i).defaultBlockState());
        helper.runAfterDelay(20, () -> {
            var hosts = positions.stream().map(pos -> (AEBaseInvBlockEntity) level.getBlockEntity(pos)).toList();
            var handlers = new ArrayList<IItemHandler>();
            for (var host : hosts) {
                host.getInternalInventory().setItemDirect(host instanceof MEChestBlockEntity ? 1 : 0,
                        host instanceof appeng.blockentity.spatial.SpatialIOPortBlockEntity ? AEItems.SPATIAL_CELL2.stack() : filledCell());
                for (var side : Direction.values()) {
                    var handler = host.getExposedItemHandler(side);
                    if (handler != null) handlers.add(handler);
                }
                handlers.add(host.getExposedItemHandler(null));
            }
            var cachedDrive = hosts.getFirst().getExposedItemHandler(null);
            helper.assertFalse(cachedDrive.extractItem(0, 1, true).isEmpty(), "public cells support simulated machine extraction");
            var terminal = PartHelper.setPart(level, center, Direction.UP, owner, MESecurity.TERMINAL_ITEM.get());
            helper.runAfterDelay(20, () -> {
                for (var handler : handlers) {
                    for (int slot = 0; slot < handler.getSlots(); slot++) {
                        // The chest input buffer is deliberately not a physical cell slot.
                        helper.assertTrue(handler.extractItem(slot, 1, true).isEmpty(), "cached simulated extraction cannot remove a cell");
                        helper.assertTrue(handler.extractItem(slot, 1, false).isEmpty(), "cached actual extraction cannot remove a cell");
                    }
                }
                var chest = (MEChestBlockEntity) hosts.get(1);
                chest.injectAEPower(1000, Actionable.MODULATE);
                var input = chest.getExposedItemHandler(chest.getFront().getOpposite());
                helper.assertTrue(input.insertItem(0, new ItemStack(Items.IRON_INGOT), false).isEmpty(), "ordinary chest input automation must remain available");
                helper.assertFalse(cachedDrive.insertItem(1, filledCell(), true).isEmpty(), "protected simulated disk insertion is denied");
                helper.assertFalse(cachedDrive.insertItem(1, filledCell(), false).isEmpty(), "protected disk insertion is denied");
                helper.assertValueEqual(1, hosts.getFirst().getInternalInventory().getStackInSlot(0).getCount(), "cached attempts preserve the installed cell");
                var hit = new BlockHitResult(Vec3.atCenterOf(center).add(0, .49, 0), Direction.UP, center, false);
                ((appeng.blockentity.networking.CableBusBlockEntity) level.getBlockEntity(center)).disassembleWithWrench(owner, level, hit, ItemStack.EMPTY);
                helper.assertTrue(SecuritySavedData.get(level.getServer()).retired(terminal.terminalId()), "owner removal retires the policy");
                helper.assertFalse(cachedDrive.extractItem(0, 1, false).isEmpty(), "the same cached handler becomes public after terminal removal");
                helper.succeed();
            });
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void explosionsProtectHardwareAndWholeCableBus(GameTestHelper helper) {
        var level = helper.getLevel();
        var center = helper.absolutePos(CENTER);
        var owner = player(level, "ExplosionOwner", center);
        terminal(level, center, owner);
        var protectedPos = center.east();
        var publicPos = center.offset(4, 0, 0);
        level.setBlockAndUpdate(protectedPos, AEBlocks.DRIVE.block().defaultBlockState());
        level.setBlockAndUpdate(publicPos, AEBlocks.DRIVE.block().defaultBlockState());
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(SecurityHardware.protectedBlock(level, protectedPos), "drive must be protected before explosion");
            helper.assertFalse(SecurityHardware.protectedBlock(level, publicPos), "isolated drive must remain public");
            level.explode(null, publicPos.getX() + .5, publicPos.getY() + .5, publicPos.getZ() + .5, 4, Level.ExplosionInteraction.BLOCK);
            helper.assertTrue(level.getBlockState(protectedPos).is(AEBlocks.DRIVE.block()), "ordinary protected drive survives a real explosion");
            helper.assertTrue(level.getBlockEntity(center) instanceof appeng.blockentity.networking.CableBusBlockEntity, "terminal-containing cable bus survives");
            helper.assertTrue(level.isEmptyBlock(publicPos), "public drive is still destructible");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 120)
    public static void craftingCompletionAndUpgradeRequireBuild(GameTestHelper helper) {
        var level = helper.getLevel();
        var center = helper.absolutePos(CENTER);
        var owner = player(level, "CpuHardwareOwner", center);
        var outsider = player(level, "CpuHardwareGuest", center);
        terminal(level, center, owner);
        var unitPos = center.east();
        level.setBlockAndUpdate(unitPos, AEBlocks.CRAFTING_UNIT.block().defaultBlockState());
        helper.runAfterDelay(20, () -> {
            var item = new ItemStack(AEBlocks.CRAFTING_STORAGE_1K.block());
            helper.assertFalse(place(level, outsider, item, unitPos, Direction.EAST).consumesAction(), "completing a CPU must check its remote exposed faces");
            helper.assertTrue(level.isEmptyBlock(unitPos.east()) && item.getCount() == 1, "denied CPU completion consumes nothing");
            helper.assertTrue(place(level, owner, new ItemStack(AEBlocks.CRAFTING_STORAGE_1K.block()), unitPos, Direction.EAST).consumesAction(), "owner may complete the CPU");
            helper.runAfterDelay(20, () -> {
                var state = level.getBlockState(unitPos);
                var component = AEItems.CELL_COMPONENT_1K.stack();
                var hit = new BlockHitResult(Vec3.atCenterOf(unitPos), Direction.WEST, unitPos, false);
                helper.assertFalse(((AbstractCraftingUnitBlock<?>) state.getBlock()).upgrade(component, state, level, unitPos, outsider, hit), "in-place upgrade cannot bypass Build");
                helper.assertTrue(component.getCount() == 1 && level.getBlockState(unitPos) == state, "denied upgrade preserves hardware and component");
                helper.assertFalse(outsider.gameMode.destroyBlock(unitPos), "every CPU member is protected");
                helper.succeed();
            });
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 120)
    public static void quantumAndPylonCompletionCheckRemoteConnections(GameTestHelper helper) {
        var level = helper.getLevel();
        var center = helper.absolutePos(CENTER);
        var owner = player(level, "MultiblockOwner", center);
        var outsider = player(level, "MultiblockGuest", center);
        terminal(level, center, owner);
        var min = center.offset(1, 0, -1);
        var missing = min.offset(2, 0, 2);
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) {
            var pos = min.offset(x, 0, z);
            if (!pos.equals(missing)) level.setBlockAndUpdate(pos, (x == 1 && z == 1
                    ? AEBlocks.QUANTUM_LINK.block() : AEBlocks.QUANTUM_RING.block()).defaultBlockState());
        }
        var pylon = center.west();
        level.setBlockAndUpdate(pylon, AEBlocks.SPATIAL_PYLON.block().defaultBlockState());
        helper.runAfterDelay(20, () -> {
            var ring = new ItemStack(AEBlocks.QUANTUM_RING.block());
            helper.assertFalse(place(level, outsider, ring, missing.north(), Direction.SOUTH).consumesAction(), "last quantum ring must check the distant cable-facing ring");
            helper.assertTrue(level.isEmptyBlock(missing) && ring.getCount() == 1, "quantum denial conserves the ring");
            var pylonItem = new ItemStack(AEBlocks.SPATIAL_PYLON.block());
            helper.assertFalse(place(level, outsider, pylonItem, pylon, Direction.WEST).consumesAction(), "pylon completion must check the entire line");
            helper.assertTrue(level.isEmptyBlock(pylon.west()) && pylonItem.getCount() == 1, "pylon denial conserves the item");
            helper.assertTrue(place(level, owner, ring, missing.north(), Direction.SOUTH).consumesAction(), "owner may complete quantum bridge");
            helper.assertTrue(place(level, owner, pylonItem, pylon, Direction.WEST).consumesAction(), "owner may complete pylon line");
            helper.succeed();
        });
    }
}
