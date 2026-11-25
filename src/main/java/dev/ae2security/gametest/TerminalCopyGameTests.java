package dev.ae2security.gametest;

import java.util.Set;
import java.util.UUID;
import appeng.api.parts.IPart;
import appeng.api.parts.PartHelper;
import appeng.api.util.AEColor;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.core.definitions.AEParts;
import com.mojang.authlib.GameProfile;
import dev.ae2security.MESecurity;
import dev.ae2security.part.SecurityTerminalPart;
import dev.ae2security.security.AccessDecision;
import dev.ae2security.security.ISecurityGrid;
import dev.ae2security.security.Permission;
import dev.ae2security.security.SecuritySavedData;
import dev.ae2security.security.TerminalLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Copying a terminal's block-entity data (WorldEdit, pick-block, structure templates, /clone) must give
 * the copy its own identity, while moving it (Spatial IO, contraptions) must keep the identity.
 */
@GameTestHolder(MESecurity.ID)
@PrefixGameTestTemplate(false)
public final class TerminalCopyGameTests {
    private static final BlockPos ORIGINAL = BlockPos.ZERO;
    private static final BlockPos ELSEWHERE = new BlockPos(4, 0, 0);

    private TerminalCopyGameTests() {}

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void copiedTerminalReceivesItsOwnIdentity(GameTestHelper helper) {
        var level = helper.getLevel();
        var original = helper.absolutePos(ORIGINAL);
        var copy = helper.absolutePos(ELSEWHERE);
        var owner = player(level, "CopyOwner", original);
        var guest = UUID.randomUUID();
        var friend = UUID.randomUUID();
        var terminal = terminal(level, original, owner);
        placeCable(level, original.above(), owner);
        helper.runAfterDelay(20, () -> {
            var data = SecuritySavedData.get(level.getServer());
            var id = terminal.terminalId();
            data.update(id, data.policy(id).trust(owner.getUUID(), friend, Permission.EXTRACT.bit()));
            var policy = data.policy(id);
            helper.assertValueEqual(location(level, original), data.location(id), "placement records the location");

            // The pasted cable carries the original identity as a saved grid hint, as a pasted region does.
            paste(level, original, copy);
            paste(level, original.above(), copy.above());
            helper.runAfterDelay(10, () -> {
                var copied = (SecurityTerminalPart) PartHelper.getPart(level, copy, Direction.NORTH);
                helper.assertTrue(copied != null, "the pasted cable bus holds a terminal");
                var copiedId = copied.terminalId();
                helper.assertValueEqual(id, terminal.terminalId(), "the original keeps its identity");
                helper.assertFalse(copiedId.equals(id), "the copy must receive its own identity");
                var copiedPolicy = data.policy(copiedId);
                helper.assertTrue(copiedPolicy != null, "the copy has a policy");
                helper.assertValueEqual(policy.owner(), copiedPolicy.owner(), "the copy keeps the owner");
                helper.assertValueEqual(policy.trusted(), copiedPolicy.trusted(), "the copy keeps the trust entries");
                helper.assertValueEqual(0L, copiedPolicy.revision(), "the copied policy starts a new history");
                helper.assertValueEqual(policy, data.policy(id), "the original policy is untouched");
                helper.assertValueEqual(location(level, original), data.location(id), "the original stays canonical");
                helper.assertValueEqual(location(level, copy), data.location(copiedId), "the copy has its own location");

                var copiedCable = PartHelper.getPart(level, copy.above(), null);
                helper.assertValueEqual(Set.of(copiedId), security(copiedCable).terminalIds(),
                        "the copy's grid must be governed only by the copy's identity");
                helper.assertValueEqual(Set.of(id), security(terminal).terminalIds(), "the original grid is unaffected");
                helper.assertValueEqual(AccessDecision.DENIED, security(copiedCable).check(guest, Permission.VIEW, false),
                        "the copy's grid stays private");
                helper.assertValueEqual(AccessDecision.ALLOW,
                        security(copiedCable).check(friend, Permission.EXTRACT, false), "copied trust applies");

                helper.runAfterDelay(10, () -> {
                    helper.assertValueEqual(Set.of(copiedId), security(copiedCable).terminalIds(),
                            "copied hints must not come back once the grid has ticked");
                    wrench(level, owner, copy);
                    helper.assertTrue(data.retired(copiedId), "removing the copy retires the copy's identity");
                    helper.assertTrue(!data.retired(id) && data.policy(id) != null,
                            "removing the copy must not retire the original identity");
                    helper.assertValueEqual(AccessDecision.DENIED, security(terminal).check(guest, Permission.VIEW, false),
                            "the original network stays protected");
                    wrench(level, owner, original);
                    helper.assertTrue(data.retired(id), "removing the original retires its identity");
                    for (var position : new BlockPos[] {ORIGINAL, ORIGINAL.above(), ELSEWHERE, ELSEWHERE.above()}) {
                        helper.destroyBlock(position);
                    }
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void movedTerminalKeepsItsIdentity(GameTestHelper helper) {
        var level = helper.getLevel();
        var original = helper.absolutePos(ORIGINAL);
        var destination = helper.absolutePos(ELSEWHERE);
        var owner = player(level, "MoveOwner", original);
        var guest = UUID.randomUUID();
        var friend = UUID.randomUUID();
        var terminal = terminal(level, original, owner);
        placeCable(level, original.above(), owner);
        helper.runAfterDelay(20, () -> {
            var data = SecuritySavedData.get(level.getServer());
            var id = terminal.terminalId();
            data.update(id, data.policy(id).trust(owner.getUUID(), friend, Permission.EXTRACT.bit()));
            var policy = data.policy(id);

            // As Spatial IO and block movers do: take the block entities out without drops, then recreate
            // them elsewhere from their saved data.
            var state = level.getBlockState(original);
            var terminalBus = level.getBlockEntity(original).saveWithFullMetadata(level.registryAccess());
            var cableBus = level.getBlockEntity(original.above()).saveWithFullMetadata(level.registryAccess());
            for (var position : new BlockPos[] {original, original.above()}) {
                level.removeBlockEntity(position);
                level.setBlock(position, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            helper.assertTrue(!data.retired(id), "precondition: taking the part out without drops keeps the policy");
            place(level, destination, state, terminalBus);
            place(level, destination.above(), state, cableBus);
            helper.runAfterDelay(10, () -> {
                var moved = (SecurityTerminalPart) PartHelper.getPart(level, destination, Direction.NORTH);
                helper.assertTrue(moved != null, "the moved cable bus holds a terminal");
                helper.assertValueEqual(id, moved.terminalId(), "a moved terminal keeps its identity");
                helper.assertValueEqual(policy, data.policy(id), "a moved terminal keeps its policy");
                helper.assertValueEqual(location(level, destination), data.location(id),
                        "a confirmed move updates the canonical location");
                var cable = PartHelper.getPart(level, destination.above(), null);
                helper.assertValueEqual(Set.of(id), security(cable).terminalIds(), "the moved grid keeps its identity");
                helper.assertValueEqual(AccessDecision.DENIED, security(cable).check(guest, Permission.VIEW, false),
                        "the moved grid stays private");
                helper.assertValueEqual(AccessDecision.ALLOW, security(cable).check(friend, Permission.EXTRACT, false),
                        "trust survives the move");
                wrench(level, owner, destination);
                helper.assertTrue(data.retired(id), "removing the moved terminal retires it normally");
                helper.destroyBlock(ELSEWHERE);
                helper.destroyBlock(ELSEWHERE.above());
                helper.succeed();
            });
        });
    }

    /** Pasting over an existing terminal reloads that live part in place with the copied identity. */
    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void identityPastedOverLiveTerminalIsReplacedByItsOwn(GameTestHelper helper) {
        var level = helper.getLevel();
        var original = helper.absolutePos(ORIGINAL);
        var target = helper.absolutePos(ELSEWHERE);
        var owner = player(level, "PasteOverOwner", original);
        var other = player(level, "PasteOverOther", target);
        var terminal = terminal(level, original, owner);
        var overwritten = terminal(level, target, other);
        helper.runAfterDelay(20, () -> {
            var data = SecuritySavedData.get(level.getServer());
            var id = terminal.terminalId();
            var previousId = overwritten.terminalId();
            var policy = data.policy(id);
            var saved = level.getBlockEntity(original).saveWithFullMetadata(level.registryAccess());
            level.getBlockEntity(target).loadWithComponents(saved, level.registryAccess());
            helper.runAfterDelay(5, () -> {
                var reloaded = (SecurityTerminalPart) PartHelper.getPart(level, target, Direction.NORTH);
                var reloadedId = reloaded.terminalId();
                helper.assertValueEqual(id, terminal.terminalId(), "the original keeps its identity");
                helper.assertFalse(reloadedId.equals(id), "the overwritten terminal must not share the copied identity");
                helper.assertFalse(reloadedId.equals(previousId), "the overwritten terminal takes the copied policy");
                helper.assertValueEqual(policy.owner(), data.policy(reloadedId).owner(), "the copied owner applies");
                helper.assertValueEqual(policy, data.policy(id), "the original policy is untouched");
                helper.assertTrue(security(reloaded).terminalIds().contains(reloadedId)
                        && !security(reloaded).terminalIds().contains(id), "the copied identity leaves the grid");
                data.retire(previousId);
                helper.destroyBlock(ORIGINAL);
                helper.destroyBlock(ELSEWHERE);
                helper.succeed();
            });
        });
    }

    private static void paste(ServerLevel level, BlockPos from, BlockPos to) {
        var source = level.getBlockEntity(from);
        place(level, to, source.getBlockState(), source.saveWithFullMetadata(level.registryAccess()));
    }

    private static void place(ServerLevel level, BlockPos position, BlockState state, CompoundTag saved) {
        level.setBlock(position, state, Block.UPDATE_ALL);
        var restored = BlockEntity.loadStatic(position, state, saved, level.registryAccess());
        if (restored == null) throw new IllegalStateException("Could not load the copied cable bus");
        level.setBlockEntity(restored);
    }

    private static void wrench(ServerLevel level, ServerPlayer player, BlockPos position) {
        player.setPos(Vec3.atCenterOf(position));
        var face = Vec3.atCenterOf(position).add(Direction.NORTH.getStepX() * 0.49, 0, Direction.NORTH.getStepZ() * 0.49);
        var hit = new BlockHitResult(face, Direction.NORTH, position, false);
        ((CableBusBlockEntity) level.getBlockEntity(position)).disassembleWithWrench(player, level, hit, ItemStack.EMPTY);
    }

    private static TerminalLocation location(ServerLevel level, BlockPos position) {
        return new TerminalLocation(level.dimension().location().toString(), position, Direction.NORTH);
    }

    private static ISecurityGrid security(IPart part) {
        return part.getGridNode().getGrid().getService(ISecurityGrid.class);
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
}
