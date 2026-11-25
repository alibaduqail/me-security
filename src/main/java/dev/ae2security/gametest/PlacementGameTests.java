package dev.ae2security.gametest;

import java.util.UUID;

import appeng.api.behaviors.PickupStrategy;
import appeng.api.parts.IPart;
import appeng.api.parts.PartHelper;
import appeng.api.util.AEColor;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.parts.automation.ItemPickupStrategy;
import com.mojang.authlib.GameProfile;
import dev.ae2security.MESecurity;
import dev.ae2security.part.SecurityTerminalPart;
import dev.ae2security.security.ISecurityGrid;
import dev.ae2security.security.Permission;
import dev.ae2security.security.SecurityBreak;
import dev.ae2security.security.SecurityHardware;
import dev.ae2security.security.SecuritySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Parts and tools that act on neighbouring hardware without joining its grid. */
@GameTestHolder(MESecurity.ID)
@PrefixGameTestTemplate(false)
public final class PlacementGameTests {
    private static final BlockPos CENTER = new BlockPos(2, 2, 2);

    private PlacementGameTests() {}

    private static ServerPlayer player(ServerLevel level, String name, BlockPos pos) {
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), name));
        player.setPos(Vec3.atCenterOf(pos));
        return player;
    }

    private static void cable(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (PartHelper.setPart(level, pos, null, player, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT)) == null) {
            throw new IllegalStateException("Could not place GameTest cable");
        }
    }

    private static SecurityTerminalPart terminal(ServerLevel level, BlockPos pos, ServerPlayer owner) {
        cable(level, pos, owner);
        return PartHelper.setPart(level, pos, Direction.UP, owner, MESecurity.TERMINAL_ITEM.get());
    }

    private static BlockHitResult hit(BlockPos pos, Direction face) {
        return new BlockHitResult(Vec3.atCenterOf(pos).add(face.getStepX() * .49, face.getStepY() * .49,
                face.getStepZ() * .49), face, pos, false);
    }

    private static InteractionResult place(ServerLevel level, ServerPlayer player, ItemStack stack,
            BlockPos against, Direction face) {
        return stack.getItem().useOn(new UseOnContext(level, player, InteractionHand.MAIN_HAND, stack, hit(against, face)));
    }

    private static InteractionResult wrench(ServerLevel level, ServerPlayer player, BlockPos pos, Direction side) {
        return ((CableBusBlockEntity) level.getBlockEntity(pos))
                .disassembleWithWrench(player, level, hit(pos, side), ItemStack.EMPTY);
    }

    private static void trust(ServerLevel level, SecurityTerminalPart terminal, ServerPlayer owner,
            ServerPlayer player, int mask) {
        var data = SecuritySavedData.get(level.getServer());
        data.update(terminal.terminalId(), data.policy(terminal.terminalId()).trust(owner.getUUID(), player.getUUID(), mask));
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void storageBusAndPlaneFacingProtectedHardwareRequireBuild(GameTestHelper helper) {
        var level = helper.getLevel();
        var center = helper.absolutePos(CENTER);
        var owner = player(level, "FacingOwner", center);
        var builder = player(level, "FacingBuilder", center);
        var outsider = player(level, "FacingGuest", center);
        var terminal = terminal(level, center, owner);
        var chest = center.west();
        var iface = center.east();
        var publicChest = center.east(3);
        level.setBlockAndUpdate(chest, AEBlocks.ME_CHEST.block().defaultBlockState());
        level.setBlockAndUpdate(iface, AEBlocks.INTERFACE.block().defaultBlockState());
        level.setBlockAndUpdate(publicChest, AEBlocks.ME_CHEST.block().defaultBlockState());
        // The template has a barrier here; the plane must reach the security check, not fail inside AE2.
        level.setBlockAndUpdate(chest.north(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(SecurityHardware.protectedBlock(level, chest) && SecurityHardware.protectedBlock(level, iface),
                    "fixture chest and interface must be protected");
            helper.assertFalse(SecurityHardware.protectedBlock(level, publicChest), "isolated chest must stay public");

            var bus = AEParts.STORAGE_BUS.stack();
            helper.assertValueEqual(InteractionResult.FAIL, place(level, outsider, bus, chest, Direction.UP),
                    "a storage bus mounting a protected ME Chest requires Build");
            helper.assertValueEqual(InteractionResult.FAIL, place(level, outsider, bus, iface, Direction.UP),
                    "a storage bus mounting a protected Interface requires Build");
            helper.assertTrue(bus.getCount() == 1 && level.isEmptyBlock(chest.above()) && level.isEmptyBlock(iface.above()),
                    "denied storage bus placement conserves the item and leaves no cable bus behind");

            var plane = AEParts.ANNIHILATION_PLANE.stack();
            helper.assertValueEqual(InteractionResult.FAIL, place(level, outsider, plane, chest, Direction.NORTH),
                    "an annihilation plane aimed at protected hardware requires Build");
            helper.assertTrue(plane.getCount() == 1 && level.isEmptyBlock(chest.north()),
                    "denied plane placement conserves the item");

            helper.assertTrue(place(level, outsider, AEParts.TERMINAL.stack(), chest, Direction.SOUTH).consumesAction(),
                    "an ordinary terminal beside protected hardware stays unrestricted");
            helper.assertTrue(place(level, outsider, AEParts.STORAGE_BUS.stack(), publicChest, Direction.UP).consumesAction(),
                    "storage buses on unprotected hardware keep AE2's normal behaviour");
            helper.assertTrue(PartHelper.getPart(level, publicChest.above(), Direction.DOWN) != null,
                    "public storage bus must be installed");

            trust(level, terminal, owner, builder, Permission.BUILD.bit());
            helper.assertTrue(place(level, builder, AEParts.STORAGE_BUS.stack(), chest, Direction.UP).consumesAction(),
                    "Build allows a storage bus on protected hardware");
            helper.assertTrue(PartHelper.getPart(level, chest.above(), Direction.DOWN) != null,
                    "authorized storage bus must be installed");
            helper.assertTrue(place(level, builder, AEParts.ANNIHILATION_PLANE.stack(), chest, Direction.NORTH).consumesAction(),
                    "Build allows an annihilation plane facing protected hardware");
            helper.succeed();
        });
    }

    private static PickupStrategy.Result annihilate(ServerLevel level, BlockPos target, ServerPlayer owner) {
        var strategy = new ItemPickupStrategy(level, target, Direction.WEST, null, ItemEnchantments.EMPTY, owner.getUUID());
        return strategy.tryPickup((amount, mode, multiplier) -> amount, (what, amount, mode) -> amount);
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void annihilationPlaneCannotDestroyProtectedHardware(GameTestHelper helper) {
        var level = helper.getLevel();
        var center = helper.absolutePos(CENTER);
        var owner = player(level, "PlaneOwner", center);
        var outsider = player(level, "PlaneGuest", center);
        var terminal = terminal(level, center, owner);
        var drive = center.east();
        var publicDrive = center.east(3);
        level.setBlockAndUpdate(drive, AEBlocks.DRIVE.block().defaultBlockState());
        level.setBlockAndUpdate(publicDrive, AEBlocks.DRIVE.block().defaultBlockState());
        helper.runAfterDelay(20, () -> {
            var data = SecuritySavedData.get(level.getServer());
            helper.assertValueEqual(PickupStrategy.Result.CANT_PICKUP, annihilate(level, drive, outsider),
                    "a plane cannot pick up a protected drive");
            helper.assertTrue(level.getBlockState(drive).is(AEBlocks.DRIVE.block()), "protected drive must survive the plane");
            helper.assertValueEqual(PickupStrategy.Result.CANT_PICKUP, annihilate(level, center, outsider),
                    "a plane cannot pick up the Security Terminal's cable bus");
            helper.assertTrue(level.getBlockEntity(center) instanceof CableBusBlockEntity
                            && !data.retired(terminal.terminalId()) && data.policy(terminal.terminalId()) != null,
                    "the terminal and its policy must survive the plane");
            helper.assertValueEqual(PickupStrategy.Result.CANT_PICKUP, annihilate(level, drive, owner),
                    "planes have no player principal, even when their owner could build");
            helper.assertValueEqual(PickupStrategy.Result.PICKED_UP, annihilate(level, publicDrive, outsider),
                    "planes still break unprotected AE2 hardware");
            helper.assertTrue(level.isEmptyBlock(publicDrive), "public drive must be removed by the plane");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void cableAnchorDoesNotLockItsCableBus(GameTestHelper helper) {
        var level = helper.getLevel();
        var center = helper.absolutePos(CENTER);
        var owner = player(level, "AnchorOwner", center);
        var builder = player(level, "AnchorBuilder", center);
        var outsider = player(level, "AnchorGuest", center);
        var terminal = terminal(level, center, owner);
        PartHelper.setPart(level, center, Direction.SOUTH, owner, AEParts.CABLE_ANCHOR.get());
        PartHelper.setPart(level, center, Direction.NORTH, owner, AEParts.CABLE_ANCHOR.get());
        var mined = center.offset(2, 0, -1);
        var wrenched = center.offset(2, 0, 1);
        for (var pos : new BlockPos[]{mined, wrenched}) {
            cable(level, pos, owner);
            PartHelper.setPart(level, pos, Direction.UP, owner, AEParts.CABLE_ANCHOR.get());
        }
        helper.runAfterDelay(20, () -> {
            helper.assertFalse(SecurityHardware.protectedBlock(level, mined),
                    "a cable anchor must not make an unprotected cable bus unresolved");
            helper.assertTrue(SecurityBreak.allowBlockBreak(level.getBlockEntity(mined), outsider)
                    && outsider.gameMode.destroyBlock(mined), "anyone may mine an unprotected anchored cable");
            helper.assertTrue(wrench(level, outsider, wrenched, Direction.UP).consumesAction()
                    && PartHelper.getPart(level, wrenched, Direction.UP) == null,
                    "anyone may wrench an anchor off an unprotected cable");

            helper.assertTrue(SecurityHardware.protectedBlock(level, center), "terminal cable bus stays protected");
            IPart south = PartHelper.getPart(level, center, Direction.SOUTH);
            helper.assertValueEqual(InteractionResult.FAIL, wrench(level, outsider, center, Direction.SOUTH),
                    "an anchor on a protected cable is authorized through that cable");
            helper.assertTrue(PartHelper.getPart(level, center, Direction.SOUTH) == south, "denied anchor removal keeps the anchor");
            helper.assertFalse(outsider.gameMode.destroyBlock(center), "outsiders cannot mine the protected anchored bus");
            trust(level, terminal, owner, builder, Permission.BUILD.bit());
            helper.assertTrue(wrench(level, builder, center, Direction.SOUTH).consumesAction()
                    && PartHelper.getPart(level, center, Direction.SOUTH) == null, "Build allows removing the anchor");
            helper.assertTrue(PartHelper.getPart(level, center, Direction.NORTH) != null, "second anchor is still installed");
            helper.assertTrue(owner.gameMode.destroyBlock(center), "the owner may mine a terminal bus that carries an anchor");
            helper.assertTrue(SecuritySavedData.get(level.getServer()).retired(terminal.terminalId()),
                    "owner removal retires the policy as usual");
            helper.succeed();
        });
    }

    private static boolean useCard(ServerLevel level, ServerPlayer player, ItemStack card, BlockPos pos, Direction side) {
        player.setItemInHand(InteractionHand.MAIN_HAND, card);
        var hit = hit(pos, side);
        var event = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit);
        NeoForge.EVENT_BUS.post(event);
        if (!event.isCanceled()) {
            card.getItem().onItemUseFirst(card, new UseOnContext(level, player, InteractionHand.MAIN_HAND, card, hit));
        }
        return !event.isCanceled();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void upgradeCardInstallRequiresBuild(GameTestHelper helper) {
        var level = helper.getLevel();
        var center = helper.absolutePos(CENTER);
        var owner = player(level, "UpgradeOwner", center);
        var guest = player(level, "UpgradeGuest", center);
        var terminal = terminal(level, center, owner);
        var bus = PartHelper.setPart(level, center, Direction.SOUTH, owner, AEParts.IMPORT_BUS.get());
        var publicPos = center.east(3);
        cable(level, publicPos, owner);
        var publicBus = PartHelper.setPart(level, publicPos, Direction.SOUTH, owner, AEParts.IMPORT_BUS.get());
        helper.runAfterDelay(20, () -> {
            var card = AEItems.SPEED_CARD.stack();
            guest.setShiftKeyDown(true);
            helper.assertFalse(useCard(level, guest, card, center, Direction.SOUTH),
                    "sneak-installing an upgrade into protected hardware requires Build");
            helper.assertTrue(card.getCount() == 1 && bus.getUpgrades().getInstalledUpgrades(AEItems.SPEED_CARD) == 0,
                    "denied upgrade conserves the card and the bus");
            guest.setShiftKeyDown(false);
            helper.assertTrue(useCard(level, guest, card, center, Direction.SOUTH),
                    "merely holding an upgrade card must not block ordinary interaction");
            helper.assertValueEqual(0, bus.getUpgrades().getInstalledUpgrades(AEItems.SPEED_CARD),
                    "a card is only installed while sneaking");

            guest.setShiftKeyDown(true);
            helper.assertTrue(useCard(level, guest, AEItems.SPEED_CARD.stack(), publicPos, Direction.SOUTH),
                    "unprotected hardware keeps AE2's quick upgrade install");
            helper.assertValueEqual(1, publicBus.getUpgrades().getInstalledUpgrades(AEItems.SPEED_CARD),
                    "public bus must receive the card");

            trust(level, terminal, owner, guest, Permission.BUILD.bit());
            helper.assertTrue(useCard(level, guest, AEItems.SPEED_CARD.stack(), center, Direction.SOUTH),
                    "Build allows the quick upgrade install");
            helper.assertValueEqual(1, bus.getUpgrades().getInstalledUpgrades(AEItems.SPEED_CARD),
                    "authorized upgrade must be installed");
            helper.assertTrue(guest.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(), "installed card leaves the hand");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void retiredTerminalIsOrdinaryHardware(GameTestHelper helper) {
        var level = helper.getLevel();
        var center = helper.absolutePos(CENTER);
        var owner = player(level, "RetiredOwner", center);
        var other = player(level, "RetiredOther", center);
        var stranger = player(level, "RetiredStranger", center);
        var live = terminal(level, center, owner);
        var stale = PartHelper.setPart(level, center, Direction.SOUTH, other, MESecurity.TERMINAL_ITEM.get());
        var lonePos = center.east(3);
        var lone = terminal(level, lonePos, other);
        helper.runAfterDelay(20, () -> {
            var data = SecuritySavedData.get(level.getServer());
            var grid = live.getGridNode().getGrid().getService(ISecurityGrid.class);
            helper.assertValueEqual(2, grid.terminalIds().size(), "fixture must start with two terminal identities");
            helper.assertValueEqual(InteractionResult.FAIL, wrench(level, stranger, lonePos, Direction.UP),
                    "a live terminal stays owner-only");

            data.retire(stale.terminalId());
            data.retire(lone.terminalId());
            helper.assertValueEqual(1, grid.terminalIds().size(), "a retired identity protects nothing");
            var host = level.getBlockEntity(center);
            helper.assertValueEqual(InteractionResult.FAIL, wrench(level, stranger, center, Direction.SOUTH),
                    "a retired terminal on a protected grid still needs Build there");
            helper.assertTrue(PartHelper.getPart(level, center, Direction.SOUTH) == stale, "denied removal keeps the part");
            helper.assertFalse(SecurityBreak.allowBlockBreak(host, stranger), "the live terminal still guards its block");
            helper.assertTrue(SecurityBreak.allowBlockBreak(host, owner),
                    "the live terminal's owner may break a block that also holds a retired terminal");
            helper.assertTrue(wrench(level, owner, center, Direction.SOUTH).consumesAction()
                    && PartHelper.getPart(level, center, Direction.SOUTH) == null,
                    "the grid's builder removes a retired terminal like any other part");
            helper.assertTrue(PartHelper.getPart(level, center, Direction.UP) == live && !data.retired(live.terminalId()),
                    "the live terminal is untouched");

            helper.assertTrue(wrench(level, stranger, lonePos, Direction.UP).consumesAction()
                    && PartHelper.getPart(level, lonePos, Direction.UP) == null,
                    "a retired terminal on an unprotected grid is removable by anyone");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void removingSidePartChecksNewCableConnection(GameTestHelper helper) {
        var level = helper.getLevel();
        var center = helper.absolutePos(CENTER);
        var owner = player(level, "ExposeOwner", center);
        var outsider = player(level, "ExposeGuest", center);
        var terminal = terminal(level, center, owner);
        var beside = center.east();
        var blocker = PartHelper.setPart(level, beside, Direction.WEST, outsider, AEParts.TERMINAL.get());
        PartHelper.setPart(level, beside, Direction.EAST, outsider, AEParts.TERMINAL.get());
        // The owner owns a node here, so joining this grid is not a claim of someone else's network.
        cable(level, beside, owner);
        // A second pair with no Security Terminal anywhere: the freed face meets a public cable.
        var publicCable = center.above(2);
        var publicBeside = publicCable.east();
        cable(level, publicCable, owner);
        PartHelper.setPart(level, publicBeside, Direction.WEST, outsider, AEParts.TERMINAL.get());
        cable(level, publicBeside, outsider);
        helper.runAfterDelay(20, () -> {
            helper.assertFalse(SecurityHardware.protectedBlock(level, publicCable), "second pair must be unprotected");
            helper.assertTrue(wrench(level, outsider, publicBeside, Direction.WEST).consumesAction()
                    && PartHelper.getPart(level, publicBeside, Direction.WEST) == null,
                    "freeing a cable face toward an unprotected grid stays unrestricted");
            var outsiderCable = PartHelper.getPart(level, beside, null);
            helper.assertTrue(outsiderCable.getGridNode().getGrid() != terminal.getGridNode().getGrid(),
                    "an occupied cable face must keep the neighbouring grids apart");
            helper.assertValueEqual(InteractionResult.FAIL, wrench(level, outsider, beside, Direction.WEST),
                    "freeing a cable face toward a protected grid requires Build on that grid");
            helper.assertTrue(PartHelper.getPart(level, beside, Direction.WEST) == blocker
                            && outsiderCable.getGridNode().getGrid() != terminal.getGridNode().getGrid(),
                    "denied removal keeps the part and the grids separate");
            helper.assertTrue(wrench(level, outsider, beside, Direction.EAST).consumesAction()
                    && PartHelper.getPart(level, beside, Direction.EAST) == null,
                    "removing a part that exposes no protected neighbour stays unrestricted");
            helper.assertTrue(wrench(level, owner, beside, Direction.WEST).consumesAction(),
                    "a player allowed to build on both grids may join them");
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(outsiderCable.getGridNode().getGrid() == terminal.getGridNode().getGrid(),
                        "the freed cable face connects to the neighbour");
                helper.succeed();
            });
        });
    }
}
