package dev.ae2security.gametest;

import java.util.*;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import appeng.api.util.AEColor;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.core.definitions.AEParts;
import com.mojang.authlib.GameProfile;
import dev.ae2security.MESecurity;
import dev.ae2security.part.SecurityTerminalPart;
import dev.ae2security.security.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-world lifecycle checks run by the dedicated GameTest server. */
@GameTestHolder(MESecurity.ID)
@PrefixGameTestTemplate(false)
public final class SecurityGameTests {
    private static final BlockPos TERMINAL_POS = BlockPos.ZERO;

    private SecurityGameTests() {}

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void terminalLifecycle(GameTestHelper helper) {
        var level = helper.getLevel();
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "SecurityOwner"));
        var trusted = UUID.randomUUID();
        var trustedPlayer = FakePlayerFactory.get(level, new GameProfile(trusted, "SecurityTrusted"));
        var outsider = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "SecurityOutsider"));

        var absolutePos = helper.absolutePos(TERMINAL_POS);
        placeCable(level, absolutePos, owner);
        var terminal = PartHelper.setPart(level, absolutePos, Direction.NORTH, owner,
                MESecurity.TERMINAL_ITEM.get());
        helper.assertTrue(terminal != null, "security terminal part must be placeable on cable");
        var terminalId = terminal.terminalId();

        helper.runAfterDelay(20, () -> {
            var node = terminal.getActionableNode();
            if (node == null || node.getGrid() == null) {
                helper.fail("Security terminal did not join an AE2 grid", TERMINAL_POS);
                return;
            }

            IGrid grid = node.getGrid();
            var security = grid.getService(ISecurityGrid.class);
            helper.assertValueEqual(AccessDecision.ALLOW,
                    security.check(owner.getUUID(), Permission.VIEW, false), "owner view access");
            helper.assertValueEqual(AccessDecision.DENIED,
                    security.check(trusted, Permission.VIEW, false), "outsider view access");

            var data = SecuritySavedData.get(level.getServer());
            data.update(terminalId, data.policy(terminalId)
                    .trust(owner.getUUID(), trusted, Permission.VIEW.bit()));
            helper.assertValueEqual(AccessDecision.ALLOW,
                    security.check(trusted, Permission.VIEW, false), "trusted view access");
            helper.assertValueEqual(AccessDecision.DENIED,
                    security.check(trusted, Permission.INSERT, false), "view-only insert access");

            var mounted = new TestStorage();
            var storageService = grid.getStorageService();
            storageService.addGlobalStorageProvider(mounts -> mounts.mount(mounted));
            var network = storageService.getInventory();
            var diamond = AEItemKey.of(Items.DIAMOND);
            var water = AEFluidKey.of(Fluids.WATER);
            var machine = IActionSource.ofMachine(terminal);
            var trustedSource = IActionSource.ofPlayer(trustedPlayer);
            var outsiderSource = IActionSource.ofPlayer(outsider);

            helper.assertValueEqual(10L, network.insert(diamond, 10, Actionable.MODULATE, machine),
                    "machine automation insertion");
            helper.assertValueEqual(0L, network.extract(diamond, 1, Actionable.SIMULATE, trustedSource),
                    "view-only extraction");
            helper.assertValueEqual(0L, network.insert(water, 1000, Actionable.SIMULATE, outsiderSource),
                    "outsider fluid simulation");
            helper.assertValueEqual(0L, mounted.amount(water), "denied simulation must not change storage");

            data.update(terminalId, data.policy(terminalId).trust(owner.getUUID(), trusted,
                    Permission.VIEW.bit() | Permission.INSERT.bit()));
            helper.assertValueEqual(1000L, network.insert(water, 1000, Actionable.SIMULATE, trustedSource),
                    "allowed fluid insertion simulation");
            helper.assertValueEqual(0L, mounted.amount(water), "simulation must not change storage");
            helper.assertValueEqual(1000L, network.insert(water, 1000, Actionable.MODULATE, trustedSource),
                    "allowed fluid insertion");
            helper.assertValueEqual(0L, network.extract(water, 250, Actionable.MODULATE, trustedSource),
                    "insert permission must not grant extraction");

            data.update(terminalId, data.policy(terminalId).trust(owner.getUUID(), trusted,
                    Permission.VIEW.bit() | Permission.EXTRACT.bit()));
            helper.assertValueEqual(4L, network.extract(diamond, 4, Actionable.SIMULATE, trustedSource),
                    "allowed item extraction simulation");
            helper.assertValueEqual(10L, mounted.amount(diamond), "extraction simulation must not change storage");
            helper.assertValueEqual(4L, network.extract(diamond, 4, Actionable.MODULATE, trustedSource),
                    "allowed item extraction");
            helper.assertValueEqual(6L, mounted.amount(diamond), "executed extraction amount");
            helper.assertValueEqual(0L, network.extract(diamond, 1, Actionable.MODULATE, outsiderSource),
                    "outsider item extraction");
            helper.assertValueEqual(6L, mounted.amount(diamond), "denied extraction must not change storage");

            helper.destroyBlock(TERMINAL_POS);
            helper.assertTrue(data.retired(terminalId), "breaking the terminal must retire its policy");
            helper.assertValueEqual(AccessDecision.ALLOW,
                    security.check(trusted, Permission.EXTRACT, false), "detached grid access");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void conflictingTerminals(GameTestHelper helper) {
        var level = helper.getLevel();
        var firstOwner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "ConflictOwnerA"));
        var secondOwner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "ConflictOwnerB"));
        var absolutePos = helper.absolutePos(TERMINAL_POS);
        placeCable(level, absolutePos, firstOwner);
        var first = PartHelper.setPart(level, absolutePos, Direction.NORTH, firstOwner, MESecurity.TERMINAL_ITEM.get());
        var second = PartHelper.setPart(level, absolutePos, Direction.SOUTH, secondOwner, MESecurity.TERMINAL_ITEM.get());
        helper.assertTrue(first != null && second != null, "two security terminal parts must fit on one cable");
        var secondId = second.terminalId();

        helper.runAfterDelay(20, () -> {
            var firstNode = first.getActionableNode();
            var secondNode = second.getActionableNode();
            if (firstNode == null || secondNode == null || firstNode.getGrid() != secondNode.getGrid()) {
                helper.fail("Adjacent security terminals did not join the same AE2 grid", TERMINAL_POS);
                return;
            }

            var security = firstNode.getGrid().getService(ISecurityGrid.class);
            helper.assertValueEqual(2, security.terminalIds().size(), "conflicting terminal count");
            helper.assertValueEqual(AccessDecision.CONFLICT,
                    security.check(firstOwner.getUUID(), Permission.VIEW, false), "first owner grid access");
            helper.assertValueEqual(AccessDecision.CONFLICT,
                    security.check(secondOwner.getUUID(), Permission.VIEW, false), "second owner grid access");
            helper.assertTrue(first.createMenu(1, firstOwner.getInventory(), firstOwner) != null,
                    "first owner must retain management access");
            helper.assertTrue(second.createMenu(2, secondOwner.getInventory(), secondOwner) != null,
                    "second owner must retain management access");
            helper.assertTrue(first.createMenu(3, secondOwner.getInventory(), secondOwner) == null,
                    "one terminal owner must not manage the other terminal");

            second.addAdditionalDrops(new ArrayList<>(), true);
            second.getHost().removePart(second);
            helper.assertTrue(SecuritySavedData.get(level.getServer()).retired(secondId),
                    "removing a conflicting terminal must retire it");
            helper.assertValueEqual(AccessDecision.ALLOW,
                    security.check(firstOwner.getUUID(), Permission.VIEW, false), "resolved owner access");
            helper.destroyBlock(TERMINAL_POS);
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void placementPermissions(GameTestHelper helper) {
        var level = helper.getLevel();
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "BuildOwner"));
        var builder = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "BuildTrusted"));
        var absolutePos = helper.absolutePos(TERMINAL_POS);
        placeCable(level, absolutePos, owner);
        var terminal = PartHelper.setPart(level, absolutePos, Direction.NORTH, owner,
                MESecurity.TERMINAL_ITEM.get());
        helper.assertTrue(terminal != null, "security terminal must be placeable before build checks");

        helper.runAfterDelay(20, () -> {
            var node = terminal.getActionableNode();
            if (node == null || node.getGrid() == null) {
                helper.fail("Security terminal did not join an AE2 grid", TERMINAL_POS);
                return;
            }

            var data = SecuritySavedData.get(level.getServer());
            data.update(terminal.terminalId(), data.policy(terminal.terminalId())
                    .trust(owner.getUUID(), builder.getUUID(), Permission.VIEW.bit()));

            var deniedBus = AEParts.STORAGE_BUS.stack();
            helper.assertValueEqual(InteractionResult.FAIL,
                    usePart(level, builder, deniedBus, absolutePos, Direction.SOUTH),
                    "view-only player part placement result");
            helper.assertValueEqual(1, deniedBus.getCount(), "denied placement must not consume the part");
            helper.assertTrue(PartHelper.getPart(level, absolutePos, Direction.SOUTH) == null,
                    "view-only player must not place a storage bus");

            helper.assertTrue(PartHelper.setPart(level, absolutePos, Direction.SOUTH, owner,
                    AEParts.STORAGE_BUS.get()) != null, "owner must be able to add a removal test part");
            helper.assertValueEqual(InteractionResult.FAIL,
                    wrenchPart(level, builder, absolutePos, Direction.SOUTH),
                    "view-only player part removal result");
            helper.assertTrue(PartHelper.getPart(level, absolutePos, Direction.SOUTH) != null,
                    "view-only player must not remove a storage bus");

            var secondTerminal = MESecurity.TERMINAL_ITEM.get().getDefaultInstance();
            helper.assertValueEqual(InteractionResult.FAIL,
                    usePart(level, owner, secondTerminal, absolutePos, Direction.WEST),
                    "second security terminal placement result");
            helper.assertValueEqual(1, secondTerminal.getCount(), "rejected second terminal must not be consumed");
            helper.assertTrue(PartHelper.getPart(level, absolutePos, Direction.WEST) == null,
                    "a protected grid must reject a second security terminal");

            data.update(terminal.terminalId(), data.policy(terminal.terminalId()).trust(owner.getUUID(),
                    builder.getUUID(), Permission.VIEW.bit() | Permission.BUILD.bit()));
            helper.assertTrue(wrenchPart(level, builder, absolutePos, Direction.SOUTH).consumesAction(),
                    "build permission must allow storage bus removal");
            helper.assertTrue(PartHelper.getPart(level, absolutePos, Direction.SOUTH) == null,
                    "authorized builder must remove a storage bus");
            helper.assertTrue(usePart(level, builder, AEParts.STORAGE_BUS.stack(), absolutePos, Direction.SOUTH)
                            .consumesAction(),
                    "build permission must allow storage bus placement");
            helper.assertTrue(PartHelper.getPart(level, absolutePos, Direction.SOUTH) != null,
                    "authorized builder must place a storage bus");

            helper.assertValueEqual(InteractionResult.FAIL,
                    wrenchPart(level, builder, absolutePos, Direction.NORTH),
                    "non-owner security terminal removal result");
            helper.assertTrue(PartHelper.getPart(level, absolutePos, Direction.NORTH) == terminal,
                    "build permission must never allow removal of another player's security terminal");
            helper.assertFalse(SecurityBreak.allowBlockBreak((CableBusBlockEntity) level.getBlockEntity(absolutePos),
                    builder), "non-owner must not break a cable-bus block containing the security terminal");
            builder.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.CREATIVE);
            helper.assertFalse(builder.gameMode.destroyBlock(absolutePos),
                    "actual creative mining must reject the terminal's non-owner");

            owner.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.CREATIVE);
            helper.assertTrue(owner.gameMode.destroyBlock(absolutePos), "owner creative mining must succeed");
            helper.assertTrue(data.retired(terminal.terminalId()), "creative mining must retire the terminal policy");

            helper.destroyBlock(TERMINAL_POS);
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void disconnectedColorsDoNotRequireBuild(GameTestHelper helper) {
        var level = helper.getLevel();
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "ColorOwner"));
        var outsider = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "ColorGuest"));
        var position = helper.absolutePos(TERMINAL_POS);
        PartHelper.setPart(level, position, null, owner, AEParts.GLASS_CABLE.item(AEColor.RED));
        var terminal = PartHelper.setPart(level, position, Direction.NORTH, owner, MESecurity.TERMINAL_ITEM.get());
        helper.setBlock(TERMINAL_POS.above(), net.minecraft.world.level.block.Blocks.AIR);
        helper.runAfterDelay(20, () -> {
            var blue = AEParts.GLASS_CABLE.stack(AEColor.BLUE, 1);
            helper.assertTrue(SecurityPlacement.allow(partContext(level, outsider, blue, position.above(), Direction.UP)),
                    "Build authorization must ignore a different cable color");
            helper.assertTrue(usePart(level, outsider, blue, position.above(), Direction.UP).consumesAction(),
                    "placing a disconnected blue cable beside a protected red cable must be allowed");
            // New cable-bus block entities initialize their nodes on AE2's next tick.
            helper.runAfterDelay(2, () -> {
                var added = PartHelper.getPart(level, position.above(), null);
                helper.assertTrue(added != null && added.getGridNode().getGrid() != terminal.getGridNode().getGrid(),
                        "differently colored cables must stay separate");
                helper.destroyBlock(TERMINAL_POS.above());
                helper.destroyBlock(TERMINAL_POS);
                helper.succeed();
            });
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void disconnectedPartsCanBePlacedAndRemoved(GameTestHelper helper) {
        var level = helper.getLevel();
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "PartOwner"));
        var outsider = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "PartGuest"));
        var position = helper.absolutePos(TERMINAL_POS);
        var terminal = PartHelper.setPart(level, position, Direction.NORTH, owner, MESecurity.TERMINAL_ITEM.get());
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(usePart(level, outsider, AEParts.TERMINAL.stack(), position, Direction.SOUTH).consumesAction(),
                    "a terminal sharing a block without a central cable is a separate network");
            var added = PartHelper.getPart(level, position, Direction.SOUTH);
            helper.assertTrue(added.getGridNode().getGrid() != terminal.getGridNode().getGrid(),
                    "side parts without a central cable must stay separate");
            helper.assertTrue(wrenchPart(level, outsider, position, Direction.SOUTH).consumesAction(),
                    "removing a disconnected part must not require Build on another part's grid");
            helper.assertTrue(PartHelper.getPart(level, position, Direction.NORTH) == terminal,
                    "the protected terminal must remain installed");
            var cable = AEParts.GLASS_CABLE.stack(AEColor.TRANSPARENT, 1);
            helper.assertValueEqual(InteractionResult.FAIL, usePart(level, outsider, cable, position, Direction.UP),
                    "adding a central cable to protected parts must require Build");
            helper.assertValueEqual(1, cable.getCount(), "denied central cable must not be consumed");
            helper.destroyBlock(TERMINAL_POS);
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void cableRecolorRequiresBuild(GameTestHelper helper) {
        var level = helper.getLevel();
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "PaintOwner"));
        var builder = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "PaintGuest"));
        var position = helper.absolutePos(TERMINAL_POS);
        var cable = PartHelper.setPart(level, position, null, owner, AEParts.GLASS_CABLE.item(AEColor.RED));
        var terminal = PartHelper.setPart(level, position, Direction.NORTH, owner, MESecurity.TERMINAL_ITEM.get());
        helper.runAfterDelay(20, () -> {
            helper.assertFalse(cable.changeColor(AEColor.BLUE, builder),
                    "a player without Build must not recolor a protected cable");
            helper.assertValueEqual(AEColor.RED, cable.getCableColor(), "denied recolor must preserve cable color");
            var data = SecuritySavedData.get(level.getServer());
            data.update(terminal.terminalId(), data.policy(terminal.terminalId()).trust(owner.getUUID(),
                    builder.getUUID(), Permission.BUILD.bit()));
            helper.assertTrue(cable.changeColor(AEColor.BLUE, builder), "Build must allow cable recoloring");
            data.update(terminal.terminalId(), data.policy(terminal.terminalId()).remove(owner.getUUID(), builder.getUUID()));
            helper.assertFalse(cable.changeColor(AEColor.RED, builder), "revocation must immediately prevent recoloring");
            helper.destroyBlock(TERMINAL_POS);
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void recoloringCannotJoinProtectedNetworks(GameTestHelper helper) {
        var level = helper.getLevel();
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "MergeOwner"));
        var position = helper.absolutePos(TERMINAL_POS);
        var firstCable = PartHelper.setPart(level, position, null, owner, AEParts.GLASS_CABLE.item(AEColor.RED));
        var first = PartHelper.setPart(level, position, Direction.NORTH, owner, MESecurity.TERMINAL_ITEM.get());
        PartHelper.setPart(level, position.above(), null, owner, AEParts.GLASS_CABLE.item(AEColor.BLUE));
        var second = PartHelper.setPart(level, position.above(), Direction.NORTH, owner, MESecurity.TERMINAL_ITEM.get());
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(first.getGridNode().getGrid() != second.getGridNode().getGrid(), "initially separate grids");
            helper.assertFalse(firstCable.changeColor(AEColor.BLUE, owner),
                    "even the owner must not merge two security terminals by recoloring");
            helper.assertValueEqual(AEColor.RED, firstCable.getCableColor(), "rejected merge must preserve color");
            helper.assertTrue(first.getGridNode().getGrid() != second.getGridNode().getGrid(),
                    "rejected recolor must not merge grids");
            helper.destroyBlock(TERMINAL_POS.above());
            helper.destroyBlock(TERMINAL_POS);
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void disconnectedToggleRemovalChecksBothGrids(GameTestHelper helper) {
        var level = helper.getLevel();
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "ToggleOwner"));
        var outsider = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "ToggleGuest"));
        var position = helper.absolutePos(TERMINAL_POS);
        placeCable(level, position, owner);
        var terminal = PartHelper.setPart(level, position, Direction.NORTH, owner, MESecurity.TERMINAL_ITEM.get());
        placeCable(level, position.above(), owner);
        var toggle = PartHelper.setPart(level, position.above(), Direction.DOWN, owner, AEParts.TOGGLE_BUS.get());
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(toggle.getGridNode().getGrid() != toggle.getExternalFacingNode().getGrid(),
                    "unpowered toggle must have separate internal and outward grids");
            helper.assertTrue(toggle.getExternalFacingNode().getGrid() == terminal.getGridNode().getGrid(),
                    "outward toggle node must touch the protected network");
            helper.assertValueEqual(InteractionResult.FAIL, wrenchPart(level, outsider, position.above(), Direction.DOWN),
                    "an outward protected grid must prevent unauthorized toggle removal");
            helper.destroyBlock(TERMINAL_POS.above());
            helper.destroyBlock(TERMINAL_POS);
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void rapidPermissionClicksUseLivePolicy(GameTestHelper helper) {
        var level = helper.getLevel();
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "ClickOwner"));
        var target = UUID.randomUUID();
        var position = helper.absolutePos(TERMINAL_POS);
        placeCable(level, position, owner);
        var terminal = PartHelper.setPart(level, position, Direction.NORTH, owner, MESecurity.TERMINAL_ITEM.get());
        helper.runAfterDelay(20, () -> {
            owner.setPos(Vec3.atCenterOf(position));
            var data = SecuritySavedData.get(level.getServer());
            data.rememberPlayer(target, "ClickGuest");
            data.update(terminal.terminalId(), data.policy(terminal.terminalId()).trust(owner.getUUID(), target, 0));
            var menu = new dev.ae2security.menu.SecurityMenu(1, owner.getInventory(), terminal);
            var revision = data.policy(terminal.terminalId()).revision();
            // A client can send several clicks before receiving any server snapshot.
            for (var permission : List.of(Permission.INSERT, Permission.BUILD)) {
                menu.edit(owner, new dev.ae2security.network.EditPolicy(1, revision, target,
                        dev.ae2security.network.EditPolicy.Action.TOGGLE_PERMISSION, permission.bit(), ""));
            }
            var policy = data.policy(terminal.terminalId());
            helper.assertTrue(policy.allows(target, Permission.INSERT), "first rapid click must be applied");
            helper.assertTrue(policy.allows(target, Permission.BUILD), "second rapid click must not be lost");
            menu.edit(owner, new dev.ae2security.network.EditPolicy(1, revision, target,
                    dev.ae2security.network.EditPolicy.Action.TOGGLE_PERMISSION, Permission.BUILD.bit(), ""));
            helper.assertFalse(data.policy(terminal.terminalId()).allows(target, Permission.BUILD),
                    "a repeated toggle must turn Build off using live server state");
            var beforeInvalid = data.policy(terminal.terminalId());
            menu.edit(owner, new dev.ae2security.network.EditPolicy(1, beforeInvalid.revision(), target,
                    dev.ae2security.network.EditPolicy.Action.TOGGLE_PERMISSION, Permission.ALL, ""));
            helper.assertValueEqual(beforeInvalid, data.policy(terminal.terminalId()),
                    "a toggle packet must not accept a combined permission mask");
            helper.destroyBlock(TERMINAL_POS);
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void verifiedSplitBecomesPublicAndRejoinProtects(GameTestHelper helper) {
        var level = helper.getLevel();
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "SplitOwner"));
        var guest = UUID.randomUUID();
        var position = helper.absolutePos(TERMINAL_POS);
        placeCable(level, position, owner);
        placeCable(level, position.above(), owner);
        placeCable(level, position.above(2), owner);
        var terminal = PartHelper.setPart(level, position, Direction.NORTH, owner, MESecurity.TERMINAL_ITEM.get());
        helper.runAfterDelay(20, () -> {
            var remote = PartHelper.getPart(level, position.above(2), null);
            helper.assertTrue(remote.getGridNode().getGrid() == terminal.getGridNode().getGrid(), "initial joined grid");
            helper.destroyBlock(TERMINAL_POS.above());
            helper.assertValueEqual(AccessDecision.DENIED, remote.getGridNode().getGrid().getService(ISecurityGrid.class)
                    .check(guest, Permission.VIEW, false), "a split must initially retain conservative restrictions");
            helper.runAfterDelay(10, () -> {
                helper.assertValueEqual(AccessDecision.ALLOW, remote.getGridNode().getGrid().getService(ISecurityGrid.class)
                        .check(guest, Permission.VIEW, false), "verified loaded disconnection must become public");
                placeCable(level, position.above(), owner);
                helper.runAfterDelay(3, () -> {
                    helper.assertValueEqual(AccessDecision.DENIED, remote.getGridNode().getGrid().getService(ISecurityGrid.class)
                            .check(guest, Permission.VIEW, false), "rejoining must immediately restore protection");
                    helper.destroyBlock(TERMINAL_POS.above(2));
                    helper.destroyBlock(TERMINAL_POS.above());
                    helper.destroyBlock(TERMINAL_POS);
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void craftingContextIsGridBound(GameTestHelper helper) {
        var level = helper.getLevel();
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "CraftOwner"));
        var crafter = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "CraftGuest"));
        var position = helper.absolutePos(TERMINAL_POS);
        PartHelper.setPart(level, position, null, owner, AEParts.GLASS_CABLE.item(AEColor.RED));
        var first = PartHelper.setPart(level, position, Direction.NORTH, owner, MESecurity.TERMINAL_ITEM.get());
        PartHelper.setPart(level, position.above(), null, owner, AEParts.GLASS_CABLE.item(AEColor.BLUE));
        var second = PartHelper.setPart(level, position.above(), Direction.NORTH, owner, MESecurity.TERMINAL_ITEM.get());
        helper.runAfterDelay(20, () -> {
            var data = SecuritySavedData.get(level.getServer());
            data.update(first.terminalId(), data.policy(first.terminalId()).trust(owner.getUUID(), crafter.getUUID(), Permission.CRAFT.bit()));
            var grid = first.getGridNode().getGrid();
            var other = second.getGridNode().getGrid();
            var normal = IActionSource.ofPlayer(crafter);
            var internal = CraftingActionSource.approved(normal, grid);
            helper.assertFalse(SecurityAccess.transfer(grid, normal, Permission.EXTRACT), "Craft is not general extraction");
            helper.assertTrue(SecurityAccess.transfer(grid, internal, Permission.EXTRACT), "approved initial ingredients may transfer");
            helper.assertFalse(SecurityAccess.transfer(other, internal, Permission.EXTRACT), "internal authorization must not cross grids");
            data.update(first.terminalId(), data.policy(first.terminalId()).remove(owner.getUUID(), crafter.getUUID()));
            helper.assertFalse(SecurityAccess.transfer(grid, internal, Permission.EXTRACT), "pending ingredients recheck revocation");
            helper.assertTrue(SecurityAccess.transfer(grid, IActionSource.ofMachine(first), Permission.EXTRACT),
                    "ongoing machine automation remains independent of player revocation");
            helper.destroyBlock(TERMINAL_POS.above());
            helper.destroyBlock(TERMINAL_POS);
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void permissionRevocationClosesOpenStorage(GameTestHelper helper) {
        var level = helper.getLevel();
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "RevokeOwner"));
        var guest = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "RevokeGuest"));
        var position = helper.absolutePos(TERMINAL_POS);
        placeCable(level, position, owner);
        var terminal = PartHelper.setPart(level, position, Direction.NORTH, owner, MESecurity.TERMINAL_ITEM.get());
        var storageTerminal = PartHelper.setPart(level, position, Direction.SOUTH, owner, AEParts.TERMINAL.get());
        helper.runAfterDelay(20, () -> {
            owner.setPos(Vec3.atCenterOf(position));
            guest.setPos(Vec3.atCenterOf(position));
            var data = SecuritySavedData.get(level.getServer());
            data.rememberPlayer(guest.getUUID(), "RevokeGuest");
            data.update(terminal.terminalId(), data.policy(terminal.terminalId()).trust(owner.getUUID(), guest.getUUID(),
                    Permission.EXTRACT.bit()));
            guest.containerMenu = new appeng.menu.me.common.MEStorageMenu(appeng.menu.me.common.MEStorageMenu.TYPE,
                    2, guest.getInventory(), storageTerminal);
            // FakePlayerFactory does not log players in. Enroll this test player only
            // for the notification check; PlayerList's public view is unmodifiable.
            List<net.minecraft.server.level.ServerPlayer> players;
            try {
                var field = net.minecraft.server.players.PlayerList.class.getDeclaredField("players");
                field.setAccessible(true);
                @SuppressWarnings("unchecked")
                var enrolled = (List<net.minecraft.server.level.ServerPlayer>) field.get(level.getServer().getPlayerList());
                players = enrolled;
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("Could not enroll the GameTest player", e);
            }
            players.add(guest);
            try {
                var menu = new dev.ae2security.menu.SecurityMenu(1, owner.getInventory(), terminal);
                menu.edit(owner, new dev.ae2security.network.EditPolicy(1, data.policy(terminal.terminalId()).revision(),
                        guest.getUUID(), dev.ae2security.network.EditPolicy.Action.PERMISSIONS, Permission.VIEW.bit(), ""));
                helper.assertTrue(guest.containerMenu == guest.inventoryMenu,
                        "revoking Extract must close the affected open storage screen even when View remains");
            } finally {
                players.remove(guest);
                guest.containerMenu = guest.inventoryMenu;
            }
            helper.destroyBlock(TERMINAL_POS);
            helper.succeed();
        });
    }

    private static InteractionResult usePart(net.minecraft.server.level.ServerLevel level,
            net.minecraft.world.entity.player.Player player, ItemStack stack, BlockPos position, Direction side) {
        return stack.getItem().useOn(partContext(level, player, stack, position, side));
    }

    private static UseOnContext partContext(net.minecraft.server.level.ServerLevel level,
            net.minecraft.world.entity.player.Player player, ItemStack stack, BlockPos position, Direction side) {
        var hitLocation = Vec3.atCenterOf(position).add(
                side.getStepX() * 0.49, side.getStepY() * 0.49, side.getStepZ() * 0.49);
        var hit = new BlockHitResult(hitLocation, side, position, false);
        return new UseOnContext(level, player, InteractionHand.MAIN_HAND, stack, hit);
    }

    private static InteractionResult wrenchPart(net.minecraft.server.level.ServerLevel level,
            net.minecraft.world.entity.player.Player player, BlockPos position, Direction side) {
        var hitLocation = Vec3.atCenterOf(position).add(
                side.getStepX() * 0.49, side.getStepY() * 0.49, side.getStepZ() * 0.49);
        var hit = new BlockHitResult(hitLocation, side, position, false);
        return ((CableBusBlockEntity) level.getBlockEntity(position))
                .disassembleWithWrench(player, level, hit, ItemStack.EMPTY);
    }

    private static void placeCable(net.minecraft.server.level.ServerLevel level, BlockPos position,
            net.minecraft.world.entity.player.Player player) {
        var cable = PartHelper.setPart(level, position, null, player,
                AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        if (cable == null) throw new IllegalStateException("Could not place GameTest cable");
    }

    private static final class TestStorage implements MEStorage {
        private final Map<AEKey, Long> amounts = new HashMap<>();

        long amount(AEKey key) {
            return amounts.getOrDefault(key, 0L);
        }

        @Override
        public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            MEStorage.checkPreconditions(key, amount, mode, source);
            if (mode == Actionable.MODULATE) amounts.merge(key, amount, Long::sum);
            return amount;
        }

        @Override
        public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            MEStorage.checkPreconditions(key, amount, mode, source);
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
            return Component.literal("ME Security GameTest Storage");
        }
    }
}
