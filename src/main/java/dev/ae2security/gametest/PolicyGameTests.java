package dev.ae2security.gametest;

import java.util.UUID;
import appeng.api.parts.PartHelper;
import appeng.api.util.AEColor;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.core.definitions.AEParts;
import com.mojang.authlib.GameProfile;
import dev.ae2security.MESecurity;
import dev.ae2security.menu.SecurityMenu;
import dev.ae2security.network.EditPolicy;
import dev.ae2security.part.SecurityTerminalPart;
import dev.ae2security.security.AccessDecision;
import dev.ae2security.security.ISecurityGrid;
import dev.ae2security.security.Permission;
import dev.ae2security.security.SecuritySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Policy persistence, placement rollback and the owner's management menu, driven as the server sees them. */
@GameTestHolder(MESecurity.ID)
@PrefixGameTestTemplate(false)
public final class PolicyGameTests {
    private static final BlockPos TERMINAL_POS = BlockPos.ZERO;
    private static final String TERMINAL_TAG = "ae2security:terminal";

    private PolicyGameTests() {}

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void orphanedTerminalReturnsToItsPlacer(GameTestHelper helper) {
        var level = helper.getLevel();
        var position = helper.absolutePos(TERMINAL_POS);
        var owner = player(level, "OrphanOwner", position);
        var outsider = player(level, "OrphanOutsider", position);
        var terminal = terminal(level, position, owner);
        helper.runAfterDelay(20, () -> {
            var data = SecuritySavedData.get(level.getServer());
            var host = terminal.getBlockEntity();
            var state = host.getBlockState();
            var saved = host.saveWithFullMetadata(level.registryAccess());
            // The chunk reached disk with a terminal id that the world data never recorded.
            var orphan = UUID.randomUUID();
            helper.assertValueEqual(1, replaceTerminalId(saved, orphan), "terminal ids in the saved cable bus");
            level.removeBlockEntity(position);
            data.retire(terminal.terminalId());
            helper.assertTrue(data.policy(orphan) == null && !data.retired(orphan), "fixture id must be unknown");
            var restored = BlockEntity.loadStatic(position, state, saved, level.registryAccess());
            helper.assertTrue(restored != null, "serialized cable bus must load");
            level.setBlockEntity(restored);
            helper.runAfterDelay(10, () -> {
                var reloaded = (SecurityTerminalPart) PartHelper.getPart(level, position, Direction.NORTH);
                helper.assertValueEqual(orphan, reloaded.terminalId(), "reload keeps the saved terminal identity");
                var policy = data.policy(orphan);
                helper.assertTrue(policy != null && policy.isOwner(owner.getUUID()),
                        "a terminal loaded without a policy must be restored to its placer");
                helper.assertTrue(policy.trusted().isEmpty(), "a recovered policy trusts nobody");
                var security = reloaded.getGridNode().getGrid().getService(ISecurityGrid.class);
                helper.assertValueEqual(AccessDecision.ALLOW,
                        security.check(owner.getUUID(), Permission.VIEW, false), "recovered owner access");
                helper.assertValueEqual(AccessDecision.DENIED,
                        security.check(outsider.getUUID(), Permission.VIEW, false), "outsider access after recovery");
                helper.assertTrue(reloaded.createMenu(1, owner.getInventory(), owner) != null,
                        "the placer can manage the recovered terminal");
                helper.assertTrue(reloaded.createMenu(2, outsider.getInventory(), outsider) == null,
                        "recovery must not open management to anyone else");
                var hit = new BlockHitResult(faceOf(position, Direction.NORTH), Direction.NORTH, position, false);
                ((CableBusBlockEntity) restored).disassembleWithWrench(owner, level, hit, ItemStack.EMPTY);
                helper.assertTrue(data.retired(orphan), "the placer can remove the recovered terminal");
                helper.destroyBlock(TERMINAL_POS);
                helper.succeed();
            });
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void obstructedPlacementLeavesNoGhostPolicy(GameTestHelper helper) {
        var level = helper.getLevel();
        var position = helper.absolutePos(TERMINAL_POS);
        // Item use of a Security Terminal needs a real player: fake players are refused.
        var owner = helper.makeMockServerPlayerInLevel();
        // Keep the connection but leave the broadcast list, or concurrent tests send AE2 packets to it.
        level.getServer().getPlayerList().remove(owner);
        owner.setPos(Vec3.atCenterOf(position).add(6, 0, 6));
        var outsider = player(level, "GhostOutsider", position);
        placeCable(level, position, owner);
        helper.runAfterDelay(20, () -> {
            var data = SecuritySavedData.get(level.getServer());
            var obstruction = EntityType.ARMOR_STAND.create(level);
            obstruction.setNoGravity(true);
            obstruction.setPos(Vec3.atBottomCenterOf(position));
            level.addFreshEntity(obstruction);

            var stack = MESecurity.TERMINAL_ITEM.get().getDefaultInstance();
            helper.assertValueEqual(InteractionResult.FAIL, usePart(level, owner, stack, position, Direction.NORTH),
                    "obstructed terminal placement result");
            helper.assertValueEqual(1, stack.getCount(), "a rolled-back placement must not consume the item");
            helper.assertTrue(PartHelper.getPart(level, position, Direction.NORTH) == null,
                    "an obstructed terminal must not stay attached");
            // Fetched afterwards: joining and leaving may have moved the cable to another grid object.
            var security = PartHelper.getPart(level, position, null).getGridNode().getGrid()
                    .getService(ISecurityGrid.class);
            helper.assertTrue(security.terminalIds().isEmpty(),
                    "a rolled-back placement must not leave its terminal identity on the grid");
            helper.assertValueEqual(AccessDecision.ALLOW,
                    security.check(outsider.getUUID(), Permission.VIEW, false),
                    "a rolled-back placement must leave the network public");
            obstruction.discard();

            helper.runAfterDelay(2, () -> {
                helper.assertTrue(usePart(level, owner, stack, position, Direction.NORTH).consumesAction(),
                        "the retry must not be rejected as a second terminal");
                var placed = (SecurityTerminalPart) PartHelper.getPart(level, position, Direction.NORTH);
                var id = placed.terminalId();
                helper.assertTrue(data.policy(id) != null && data.policy(id).isOwner(owner.getUUID()),
                        "the retry binds the terminal to its placer");

                // An unload takes the whole host out of the world, even in the tick the policy was made.
                var host = placed.getBlockEntity();
                var state = host.getBlockState();
                var saved = host.saveWithFullMetadata(level.registryAccess());
                level.removeBlockEntity(position);
                helper.assertFalse(data.retired(id), "unloading a just-placed terminal is not a rollback");
                level.setBlockEntity(BlockEntity.loadStatic(position, state, saved, level.registryAccess()));
                helper.runAfterDelay(10, () -> {
                    helper.assertTrue(data.policy(id) != null && data.policy(id).isOwner(owner.getUUID()),
                            "the policy survives the reload");
                    helper.destroyBlock(TERMINAL_POS);
                    helper.assertTrue(data.retired(id), "breaking the block still retires the policy");
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void editBudgetAbsorbsFloodsNotClicks(GameTestHelper helper) {
        var level = helper.getLevel();
        var position = helper.absolutePos(TERMINAL_POS);
        var owner = player(level, "BudgetOwner", position);
        var guest = UUID.randomUUID();
        var terminal = terminal(level, position, owner);
        helper.runAfterDelay(20, () -> {
            var data = SecuritySavedData.get(level.getServer());
            var id = terminal.terminalId();
            data.rememberPlayer(guest, "BudgetGuest");
            data.update(id, data.policy(id).trust(owner.getUUID(), guest, 0));
            var menu = open(owner, terminal, 1);
            long before = data.policy(id).revision();
            int sent = SecurityMenu.EDIT_BURST + 25;
            // Every one of these is a valid toggle: an applied edit raises the revision by one.
            for (int i = 0; i < sent; i++) {
                menu.edit(owner, new EditPolicy(1, before, guest, EditPolicy.Action.TOGGLE_PERMISSION,
                        Permission.INSERT.bit(), ""));
            }
            long applied = data.policy(id).revision() - before;
            helper.assertTrue(applied >= SecurityMenu.EDIT_BURST,
                    "a burst of clicks must all be applied, but only " + applied + " were");
            helper.assertTrue(applied < sent, "edits beyond the budget must not be applied: " + applied);
            helper.assertTrue(owner.containerMenu == menu, "running out of budget must not close the screen");
            close(owner);
            helper.destroyBlock(TERMINAL_POS);
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void transferNeedsTheTypedNameAndLocksOutThePreviousOwner(GameTestHelper helper) {
        var level = helper.getLevel();
        var position = helper.absolutePos(TERMINAL_POS);
        var owner = player(level, "TransferOwner", position);
        var heir = UUID.randomUUID();
        var keeper = UUID.randomUUID();
        var terminal = terminal(level, position, owner);
        helper.runAfterDelay(20, () -> {
            var data = SecuritySavedData.get(level.getServer());
            var id = terminal.terminalId();
            data.rememberPlayer(heir, "PolicyHeir");
            data.rememberPlayer(keeper, "PolicyKeeper");
            data.update(id, data.policy(id).trust(owner.getUUID(), heir, 0)
                    .trust(owner.getUUID(), keeper, Permission.EXTRACT.bit()));
            var menu = open(owner, terminal, 1);
            var before = data.policy(id);

            menu.edit(owner, edit(1, before.revision(), heir, EditPolicy.Action.TRANSFER, "PolicyKeeper"));
            helper.assertValueEqual(before, data.policy(id), "policy after a transfer with the wrong name");
            menu.edit(owner, edit(1, before.revision(), heir, EditPolicy.Action.TRANSFER, ""));
            helper.assertValueEqual(before, data.policy(id), "policy after a transfer with no name");
            menu.edit(owner, edit(1, before.revision() - 1, heir, EditPolicy.Action.TRANSFER, "PolicyHeir"));
            helper.assertValueEqual(before, data.policy(id), "policy after a transfer from a stale revision");
            helper.assertTrue(owner.containerMenu == menu, "a rejected transfer keeps the screen open");

            menu.edit(owner, edit(1, before.revision(), heir, EditPolicy.Action.TRANSFER, "  policyheir "));
            var after = data.policy(id);
            helper.assertTrue(after.isOwner(heir), "the confirmed transfer changes the owner");
            helper.assertFalse(after.trusted().containsKey(heir), "the new owner is no longer a trusted entry");
            helper.assertTrue(after.allows(keeper, Permission.EXTRACT), "other trusted entries survive a transfer");
            helper.assertValueEqual(0, after.permissions(owner.getUUID()), "previous owner's permissions");
            helper.assertTrue(owner.containerMenu == owner.inventoryMenu, "a transfer closes the previous owner's screen");
            helper.assertTrue(terminal.createMenu(2, owner.getInventory(), owner) == null,
                    "the previous owner cannot reopen management");
            helper.assertValueEqual(AccessDecision.DENIED, terminal.getGridNode().getGrid()
                    .getService(ISecurityGrid.class).check(owner.getUUID(), Permission.VIEW, false),
                    "previous owner's network access");

            // A screen the previous owner still held open is refused and closed.
            var stale = open(owner, terminal, 3);
            stale.edit(owner, edit(3, after.revision(), keeper, EditPolicy.Action.REMOVE, ""));
            helper.assertValueEqual(after, data.policy(id), "policy after the previous owner's edit");
            helper.assertTrue(owner.containerMenu == owner.inventoryMenu, "a denied edit closes the screen");
            helper.destroyBlock(TERMINAL_POS);
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void trustAndRemoveApplyOnlyAtTheLiveRevision(GameTestHelper helper) {
        var level = helper.getLevel();
        var position = helper.absolutePos(TERMINAL_POS);
        var owner = player(level, "TrustOwner", position);
        var guest = UUID.randomUUID();
        var stranger = UUID.randomUUID();
        var terminal = terminal(level, position, owner);
        helper.runAfterDelay(20, () -> {
            var data = SecuritySavedData.get(level.getServer());
            var id = terminal.terminalId();
            data.rememberPlayer(guest, "TrustGuest");
            var menu = open(owner, terminal, 1);
            var initial = data.policy(id);

            menu.edit(owner, edit(1, initial.revision(), stranger, EditPolicy.Action.TRUST, ""));
            helper.assertValueEqual(initial, data.policy(id), "policy after trusting a player the server never saw");
            menu.edit(owner, edit(1, initial.revision(), owner.getUUID(), EditPolicy.Action.TRUST, ""));
            menu.edit(owner, edit(1, initial.revision(), owner.getUUID(), EditPolicy.Action.REMOVE, ""));
            helper.assertValueEqual(initial, data.policy(id), "policy after editing the owner's own entry");
            menu.edit(owner, edit(1, initial.revision() + 1, guest, EditPolicy.Action.TRUST, ""));
            helper.assertValueEqual(initial, data.policy(id), "policy after a trust from a future revision");

            menu.edit(owner, edit(1, initial.revision(), guest, EditPolicy.Action.TRUST, ""));
            var trusted = data.policy(id);
            helper.assertValueEqual(Permission.VIEW.bit(), trusted.permissions(guest),
                    "a newly trusted player's permissions");

            data.update(id, trusted.trust(owner.getUUID(), guest, Permission.EXTRACT.bit()));
            var granted = data.policy(id);
            // Both were built against the screen as it looked one revision ago.
            menu.edit(owner, edit(1, trusted.revision(), guest, EditPolicy.Action.TRUST, ""));
            menu.edit(owner, edit(1, trusted.revision(), guest, EditPolicy.Action.REMOVE, ""));
            helper.assertValueEqual(granted, data.policy(id), "policy after stale trust and remove requests");

            menu.edit(owner, edit(1, granted.revision(), guest, EditPolicy.Action.REMOVE, ""));
            helper.assertFalse(data.policy(id).trusted().containsKey(guest), "remove must end trust");
            helper.assertValueEqual(0, data.policy(id).permissions(guest), "removed player's permissions");
            helper.assertTrue(owner.containerMenu == menu, "ordinary edits keep the screen open");
            close(owner);
            helper.destroyBlock(TERMINAL_POS);
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void onlyThePresentOwnerCanEdit(GameTestHelper helper) {
        var level = helper.getLevel();
        var position = helper.absolutePos(TERMINAL_POS);
        var owner = player(level, "EditOwner", position);
        var guest = player(level, "EditGuest", position);
        var accomplice = UUID.randomUUID();
        var terminal = terminal(level, position, owner);
        helper.runAfterDelay(20, () -> {
            var data = SecuritySavedData.get(level.getServer());
            var id = terminal.terminalId();
            data.rememberPlayer(guest.getUUID(), "EditGuest");
            data.rememberPlayer(accomplice, "EditAccomplice");
            data.update(id, data.policy(id).trust(owner.getUUID(), guest.getUUID(), Permission.ALL));
            var policy = data.policy(id);
            var trust = edit(1, policy.revision(), accomplice, EditPolicy.Action.TRUST, "");

            helper.assertTrue(terminal.createMenu(1, guest.getInventory(), guest) == null,
                    "full permissions never include management");
            // A modified client cannot conjure the menu, but the server must not depend on that.
            var forged = open(guest, terminal, 1);
            forged.edit(guest, trust);
            helper.assertValueEqual(policy, data.policy(id), "policy after a trusted player's edit");
            helper.assertTrue(guest.containerMenu == guest.inventoryMenu, "a non-owner's menu is closed");

            var menu = open(owner, terminal, 1);
            menu.edit(guest, trust);
            helper.assertValueEqual(policy, data.policy(id), "policy after another player used the owner's menu");
            owner.containerMenu = owner.inventoryMenu;
            menu.edit(owner, trust);
            helper.assertValueEqual(policy, data.policy(id), "policy after an edit through a menu that is not open");

            owner.containerMenu = menu;
            owner.setPos(Vec3.atCenterOf(position.offset(20, 0, 0)));
            menu.edit(owner, trust);
            helper.assertValueEqual(policy, data.policy(id), "policy after an edit from out of reach");
            helper.assertTrue(owner.containerMenu == owner.inventoryMenu, "walking away closes the menu");

            owner.setPos(Vec3.atCenterOf(position));
            open(owner, terminal, 2).edit(owner, edit(2, policy.revision(), accomplice, EditPolicy.Action.TRUST, ""));
            helper.assertTrue(data.policy(id).trusted().containsKey(accomplice), "the present owner's edit applies");
            close(owner);
            helper.destroyBlock(TERMINAL_POS);
            helper.succeed();
        });
    }

    private static ServerPlayer player(ServerLevel level, String name, BlockPos near) {
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), name));
        player.setPos(Vec3.atCenterOf(near));
        return player;
    }

    private static SecurityTerminalPart terminal(ServerLevel level, BlockPos position, ServerPlayer owner) {
        placeCable(level, position, owner);
        var terminal = PartHelper.setPart(level, position, Direction.NORTH, owner, MESecurity.TERMINAL_ITEM.get());
        if (terminal == null) throw new IllegalStateException("Could not place GameTest security terminal");
        return terminal;
    }

    private static void placeCable(ServerLevel level, BlockPos position, ServerPlayer player) {
        if (PartHelper.setPart(level, position, null, player, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT)) == null) {
            throw new IllegalStateException("Could not place GameTest cable");
        }
    }

    /** Opens the menu the way the server holds it, without the ownership check in createMenu. */
    private static SecurityMenu open(ServerPlayer player, SecurityTerminalPart terminal, int containerId) {
        var menu = new SecurityMenu(containerId, player.getInventory(), terminal);
        player.containerMenu = menu;
        return menu;
    }

    private static void close(ServerPlayer player) {
        player.containerMenu = player.inventoryMenu;
    }

    private static EditPolicy edit(int menuId, long revision, UUID target, EditPolicy.Action action,
            String confirmation) {
        return new EditPolicy(menuId, revision, target, action, 0, confirmation);
    }

    private static Vec3 faceOf(BlockPos position, Direction side) {
        return Vec3.atCenterOf(position).add(side.getStepX() * 0.49, side.getStepY() * 0.49, side.getStepZ() * 0.49);
    }

    private static InteractionResult usePart(ServerLevel level, ServerPlayer player, ItemStack stack,
            BlockPos position, Direction side) {
        var hit = new BlockHitResult(faceOf(position, side), side, position, false);
        return stack.getItem().useOn(new UseOnContext(level, player, InteractionHand.MAIN_HAND, stack, hit));
    }

    /** Rewrites every saved terminal identity below this tag and reports how many it found. */
    private static int replaceTerminalId(Tag tag, UUID id) {
        int replaced = 0;
        if (tag instanceof CompoundTag compound) {
            if (compound.hasUUID(TERMINAL_TAG)) {
                compound.putUUID(TERMINAL_TAG, id);
                replaced++;
            }
            for (var key : compound.getAllKeys()) replaced += replaceTerminalId(compound.get(key), id);
        } else if (tag instanceof ListTag list) {
            for (var element : list) replaced += replaceTerminalId(element, id);
        }
        return replaced;
    }
}
