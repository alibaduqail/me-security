package dev.ae2security.gametest;

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import appeng.api.parts.IPart;
import appeng.api.parts.PartHelper;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEParts;
import com.mojang.authlib.GameProfile;
import dev.ae2security.MESecurity;
import dev.ae2security.security.AccessDecision;
import dev.ae2security.security.ISecurityGrid;
import dev.ae2security.security.Permission;
import dev.ae2security.security.SecuritySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Pins how a terminal's remembered chunk footprint follows its network: chunks the network has provably
 * left are forgotten so they stop holding split-off pieces, and chunks that cannot be checked are kept.
 */
@GameTestHolder(MESecurity.ID)
@PrefixGameTestTemplate(false)
public final class FootprintGameTests {
    private static final BlockPos ORIGIN = BlockPos.ZERO;
    /** High enough above the template that the run cannot touch another test's hardware. */
    private static final int RUN_HEIGHT = 24;

    private FootprintGameTests() {}

    /**
     * A cable run into the next chunk is recorded, then removed. Once that chunk has stayed loaded without
     * the run, it is forgotten, and a piece split off later is released on the remaining footprint alone.
     */
    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 700)
    public static void removedRunIsForgottenAndLaterSplitReleases(GameTestHelper helper) {
        var level = helper.getLevel();
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "FootprintRunOwner"));
        var guest = UUID.randomUUID();
        var base = helper.absolutePos(ORIGIN);
        var top = base.above(RUN_HEIGHT);
        var far = top.east(16 - (base.getX() & 15));
        var farChunk = new ChunkPos(far);
        level.setChunkForced(farChunk.x, farChunk.z, true);
        // Load the forced chunk's neighbours now so that it ticks entities without waiting on generation.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) level.getChunk(farChunk.x + dx, farChunk.z + dz);
        }
        var placed = new ArrayList<BlockPos>();
        for (int y = 0; y <= RUN_HEIGHT; y++) placed.add(placeCable(level, base.above(y), owner));
        for (var pos = top.east(); pos.getX() <= far.getX(); pos = pos.east()) placed.add(placeCable(level, pos, owner));
        var terminal = PartHelper.setPart(level, base, Direction.NORTH, owner, MESecurity.TERMINAL_ITEM.get());
        helper.assertTrue(terminal != null, "security terminal part must be placeable on cable");
        var id = terminal.terminalId();
        var data = SecuritySavedData.get(level.getServer());
        var home = key(level, new ChunkPos(base));
        var away = key(level, farChunk);

        helper.runAfterDelay(20, () -> {
            helper.assertTrue(PartHelper.getPart(level, far, null).getGridNode().getGrid()
                    == terminal.getGridNode().getGrid(), "the run reaches the terminal's grid");
            helper.assertValueEqual(Set.of(home, away), data.footprint(id), "the run's chunk is remembered");
            level.destroyBlock(far, false);
            placed.remove(far);
            helper.assertTrue(level.getBlockState(far).isAir(), "the run's last cable is removed");
            helper.assertTrue(!data.footprint(id).isEmpty() && PartHelper.getPart(level, far.west(), null) != null,
                    "the rest of the run stays");
            helper.assertTrue(data.footprint(id).contains(away),
                    "a chunk the network just left is not forgotten before it has been checked");

            int removedAt = level.getServer().getTickCount();
            await(helper, 500, () -> !data.footprint(id).contains(away), () -> "the run's chunk is still remembered;"
                    + " ready=" + SecuritySavedData.chunkReady(level.getServer(), away) + " homeReady="
                    + SecuritySavedData.chunkReady(level.getServer(), home) + " far=" + level.getBlockState(far), () -> {
                helper.assertTrue(level.getServer().getTickCount() - removedAt > 40,
                        "the chunk is not forgotten before it has stayed ready for the settle time");
                helper.assertValueEqual(Set.of(home), data.footprint(id),
                        "a chunk that stayed loaded without the network is forgotten");
                var remote = PartHelper.getPart(level, base.above(2), null);
                level.destroyBlock(base.above(), false);
                placed.remove(base.above());
                helper.assertValueEqual(AccessDecision.DENIED, security(remote).check(guest, Permission.VIEW, false),
                        "a split must initially retain conservative restrictions");
                helper.runAfterDelay(12, () -> {
                    helper.assertValueEqual(AccessDecision.ALLOW,
                            security(remote).check(guest, Permission.VIEW, false),
                            "the split is verified without the forgotten chunk");
                    for (int i = placed.size() - 1; i >= 0; i--) level.destroyBlock(placed.get(i), false);
                    level.setChunkForced(farChunk.x, farChunk.z, false);
                    helper.succeed();
                });
            });
        });
    }

    /** A remembered chunk that is not loaded can never be checked, so it keeps a split piece protected. */
    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 200)
    public static void unloadedRememberedChunkKeepsSplitProtected(GameTestHelper helper) {
        var level = helper.getLevel();
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "FootprintUnloadedOwner"));
        var guest = UUID.randomUUID();
        var base = helper.absolutePos(ORIGIN);
        var terminal = placeStack(level, base, owner);
        helper.assertTrue(terminal != null, "security terminal part must be placeable on cable");
        var id = terminal.terminalId();
        var data = SecuritySavedData.get(level.getServer());
        // As if the network once ran through a chunk far beyond anything loaded.
        var unloaded = key(level, new ChunkPos((base.getX() >> 4) + 4096, base.getZ() >> 4));

        helper.runAfterDelay(20, () -> {
            helper.assertTrue(!level.hasChunk(unloaded.x(), unloaded.z()), "precondition: the chunk is not loaded");
            data.reconcileChunks(id, Set.of(unloaded), chunk -> false);
            var remote = PartHelper.getPart(level, base.above(2), null);
            helper.destroyBlock(ORIGIN.above());
            helper.runAfterDelay(70, () -> {
                helper.assertTrue(data.footprint(id).contains(unloaded), "an unloaded chunk is never forgotten");
                helper.assertValueEqual(AccessDecision.DENIED, security(remote).check(guest, Permission.VIEW, false),
                        "a split that cannot be verified stays protected");
                helper.destroyBlock(ORIGIN.above(2));
                helper.destroyBlock(ORIGIN);
                helper.succeed();
            });
        });
    }

    /** A chunk in a dimension that no longer exists cannot hold a connection and must not block release. */
    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void chunkInMissingDimensionIsForgotten(GameTestHelper helper) {
        var level = helper.getLevel();
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "FootprintDimensionOwner"));
        var guest = UUID.randomUUID();
        var base = helper.absolutePos(ORIGIN);
        var terminal = placeStack(level, base, owner);
        helper.assertTrue(terminal != null, "security terminal part must be placeable on cable");
        var id = terminal.terminalId();
        var data = SecuritySavedData.get(level.getServer());
        var missing = new SecuritySavedData.ChunkKey(MESecurity.ID + ":gametest_removed_dimension", 0, 0);

        helper.runAfterDelay(20, () -> {
            data.reconcileChunks(id, Set.of(missing), chunk -> false);
            helper.assertTrue(!data.topologyLoaded(id, level.getServer()),
                    "precondition: the missing dimension blocks proof while it is remembered");
            var remote = PartHelper.getPart(level, base.above(2), null);
            helper.destroyBlock(ORIGIN.above());
            helper.assertValueEqual(AccessDecision.DENIED, security(remote).check(guest, Permission.VIEW, false),
                    "a split must initially retain conservative restrictions");
            helper.runAfterDelay(12, () -> {
                helper.assertTrue(!data.footprint(id).contains(missing), "the missing dimension is forgotten");
                helper.assertValueEqual(AccessDecision.ALLOW, security(remote).check(guest, Permission.VIEW, false),
                        "the split is verified once the missing dimension is forgotten");
                helper.destroyBlock(ORIGIN.above(2));
                helper.destroyBlock(ORIGIN);
                helper.succeed();
            });
        });
    }

    /** Runs then once condition holds, checking every tick; fails after ticks ticks. */
    private static void await(GameTestHelper helper, int ticks, BooleanSupplier condition,
            java.util.function.Supplier<String> failure, Runnable then) {
        if (condition.getAsBoolean()) {
            then.run();
        } else if (ticks <= 0) {
            helper.fail(failure.get());
        } else {
            helper.runAfterDelay(1, () -> await(helper, ticks - 1, condition, failure, then));
        }
    }

    private static SecuritySavedData.ChunkKey key(ServerLevel level, ChunkPos chunk) {
        return new SecuritySavedData.ChunkKey(level.dimension().location().toString(), chunk.x, chunk.z);
    }

    /** A terminal on the bottom of a three-cable stack, as the split tests in GridServiceGameTests build it. */
    private static dev.ae2security.part.SecurityTerminalPart placeStack(ServerLevel level, BlockPos base, Player owner) {
        placeCable(level, base, owner);
        placeCable(level, base.above(), owner);
        placeCable(level, base.above(2), owner);
        return PartHelper.setPart(level, base, Direction.NORTH, owner, MESecurity.TERMINAL_ITEM.get());
    }

    private static ISecurityGrid security(IPart part) {
        return part.getGridNode().getGrid().getService(ISecurityGrid.class);
    }

    private static BlockPos placeCable(ServerLevel level, BlockPos position, Player player) {
        var cable = PartHelper.setPart(level, position, null, player, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        if (cable == null) throw new IllegalStateException("Could not place GameTest cable");
        return position.immutable();
    }
}
