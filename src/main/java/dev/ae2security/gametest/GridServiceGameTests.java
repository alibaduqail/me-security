package dev.ae2security.gametest;

import java.util.Set;
import java.util.UUID;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Pins what the grid service reports in the same tick the set of terminals on a grid changes, which is
 * where a cached or incrementally maintained identity set would go stale first.
 */
@GameTestHolder(MESecurity.ID)
@PrefixGameTestTemplate(false)
public final class GridServiceGameTests {
    private static final BlockPos ORIGIN = BlockPos.ZERO;
    private static final String PART_IDENTITY_TAG = "ae2security:terminal";

    private GridServiceGameTests() {}

    /** A grid that has ticked without any identity must be protected by a terminal the moment it joins. */
    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void terminalJoiningIdleGridProtectsInTheSameTick(GameTestHelper helper) {
        var level = helper.getLevel();
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "GridServiceJoinOwner"));
        var guest = UUID.randomUUID();
        var position = helper.absolutePos(ORIGIN);
        placeCable(level, position, owner);
        placeCable(level, position.above(), owner);
        placeCable(level, position.above(2), owner);
        helper.runAfterDelay(10, () -> {
            var remote = PartHelper.getPart(level, position.above(2), null);
            helper.assertTrue(security(remote).terminalIds().isEmpty(), "a grid without terminals has no identities");
            helper.assertValueEqual(AccessDecision.ALLOW, security(remote).check(guest, Permission.VIEW, false),
                    "a grid without terminals is public");

            var terminal = PartHelper.setPart(level, position, Direction.NORTH, owner, MESecurity.TERMINAL_ITEM.get());
            helper.assertTrue(terminal != null, "security terminal part must be placeable on cable");
            var terminalId = terminal.terminalId();
            helper.assertTrue(remote.getGridNode().getGrid() == terminal.getGridNode().getGrid(),
                    "the terminal joins the cable grid when it is placed");
            helper.assertValueEqual(Set.of(terminalId), security(remote).terminalIds(),
                    "the new terminal must govern the grid before the next tick");
            helper.assertValueEqual(AccessDecision.DENIED, security(remote).check(guest, Permission.VIEW, false),
                    "a guest must be denied in the tick the terminal is placed");
            helper.assertValueEqual(AccessDecision.ALLOW,
                    security(remote).check(owner.getUUID(), Permission.VIEW, false), "the owner keeps access");

            helper.runAfterDelay(20, () -> {
                helper.assertValueEqual(AccessDecision.DENIED, security(remote).check(guest, Permission.VIEW, false),
                        "protection must persist once the grid has ticked");
                helper.destroyBlock(ORIGIN.above());
                helper.assertValueEqual(AccessDecision.DENIED, security(remote).check(guest, Permission.VIEW, false),
                        "a split must initially retain conservative restrictions");
                // Same expectation as SecurityGameTests.verifiedSplitBecomesPublicAndRejoinProtects; it only
                // holds here if the chunk footprint was recorded for a terminal that joined an idle grid.
                helper.runAfterDelay(10, () -> {
                    helper.assertValueEqual(AccessDecision.ALLOW,
                            security(remote).check(guest, Permission.VIEW, false),
                            "verified loaded disconnection must become public");
                    helper.destroyBlock(ORIGIN.above(2));
                    helper.destroyBlock(ORIGIN);
                    helper.succeed();
                });
            });
        });
    }

    /**
     * AE2 reloads a live part in place when its block entity is given new NBT, and the terminal replaces
     * its identity only after AE2 has finished with the node. The grid must report the identity the part
     * carries now, not the one it carried when its node was added.
     */
    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void identityReloadedOnLivePartIsReportedInTheSameTick(GameTestHelper helper) {
        var level = helper.getLevel();
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "GridServiceReloadOwner"));
        var position = helper.absolutePos(ORIGIN);
        placeCable(level, position, owner);
        var terminal = PartHelper.setPart(level, position, Direction.NORTH, owner, MESecurity.TERMINAL_ITEM.get());
        helper.assertTrue(terminal != null, "security terminal part must be placeable on cable");
        var originalId = terminal.terminalId();
        helper.runAfterDelay(5, () -> {
            var tag = new CompoundTag();
            terminal.writeToNBT(tag, level.registryAccess());
            helper.assertTrue(tag.hasUUID(PART_IDENTITY_TAG), "the part saves its identity");
            var reloadedId = UUID.randomUUID();
            tag.putUUID(PART_IDENTITY_TAG, reloadedId);
            terminal.readFromNBT(tag, level.registryAccess());
            helper.assertValueEqual(reloadedId, terminal.terminalId(), "the part adopts the identity in its NBT");

            var service = security(terminal);
            helper.assertTrue(service.terminalIds().contains(reloadedId),
                    "the grid must report the identity the attached part carries now");
            helper.assertTrue(service.check(owner.getUUID(), Permission.VIEW, false) != AccessDecision.ALLOW,
                    "an identity without a policy must never resolve to public access");

            var data = SecuritySavedData.get(level.getServer());
            data.retire(originalId);
            helper.destroyBlock(ORIGIN);
            helper.succeed();
        });
    }

    /** An identity must stay inherited in the tick its part leaves the grid without being retired. */
    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void terminalLeavingWithoutRetirementStaysInheritedInTheSameTick(GameTestHelper helper) {
        var level = helper.getLevel();
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "GridServiceLeaveOwner"));
        var guest = UUID.randomUUID();
        var position = helper.absolutePos(ORIGIN);
        placeCable(level, position, owner);
        placeCable(level, position.above(), owner);
        var terminal = PartHelper.setPart(level, position, Direction.NORTH, owner, MESecurity.TERMINAL_ITEM.get());
        helper.assertTrue(terminal != null, "security terminal part must be placeable on cable");
        var terminalId = terminal.terminalId();
        helper.runAfterDelay(20, () -> {
            var cable = PartHelper.getPart(level, position.above(), null);
            var data = SecuritySavedData.get(level.getServer());
            helper.assertValueEqual(AccessDecision.DENIED, security(cable).check(guest, Permission.VIEW, false),
                    "the grid is protected while the terminal is attached");

            // No drops are collected, as when AE2 takes a part out of the world without breaking it.
            terminal.getHost().removePart(terminal);
            helper.assertTrue(!data.retired(terminalId),
                    "precondition: removing a part without collecting its drops does not retire its policy");
            helper.assertValueEqual(Set.of(terminalId), security(cable).terminalIds(),
                    "the identity must stay inherited in the tick its part leaves");
            helper.assertValueEqual(AccessDecision.DENIED, security(cable).check(guest, Permission.VIEW, false),
                    "a guest must stay denied in the tick the terminal part leaves");

            data.retire(terminalId);
            helper.assertValueEqual(AccessDecision.ALLOW, security(cable).check(guest, Permission.VIEW, false),
                    "retirement must take effect without waiting for a tick");
            helper.destroyBlock(ORIGIN.above());
            helper.destroyBlock(ORIGIN);
            helper.succeed();
        });
    }

    private static ISecurityGrid security(IPart part) {
        return part.getGridNode().getGrid().getService(ISecurityGrid.class);
    }

    private static void placeCable(ServerLevel level, BlockPos position, Player player) {
        var cable = PartHelper.setPart(level, position, null, player, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        if (cable == null) throw new IllegalStateException("Could not place GameTest cable");
    }
}
