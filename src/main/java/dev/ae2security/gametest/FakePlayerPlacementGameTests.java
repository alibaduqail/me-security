package dev.ae2security.gametest;

import java.util.UUID;

import appeng.api.parts.PartHelper;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEParts;
import com.mojang.authlib.GameProfile;
import dev.ae2security.MESecurity;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Machines must not become Security Terminal owners through normal item use. */
@GameTestHolder(MESecurity.ID)
@PrefixGameTestTemplate(false)
public final class FakePlayerPlacementGameTests {
    private static final BlockPos CENTER = new BlockPos(2, 2, 2);

    private FakePlayerPlacementGameTests() {}

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void fakePlayerCannotPlaceTerminalButPlayerCan(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(CENTER);
        var machine = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "deployer"));
        var real = helper.makeMockServerPlayerInLevel();
        // Keep the connection but leave the broadcast list, or concurrent tests send AE2 packets to it.
        level.getServer().getPlayerList().remove(real);
        real.setPos(Vec3.atCenterOf(pos).add(6, 0, 6));
        helper.assertFalse(real instanceof FakePlayer, "the mock player must not be a FakePlayer");
        machine.setPos(Vec3.atCenterOf(pos));
        if (PartHelper.setPart(level, pos, null, real, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT)) == null) {
            throw new IllegalStateException("Could not place GameTest cable");
        }
        var hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, .49, 0), Direction.UP, pos, false);

        var denied = new ItemStack(MESecurity.TERMINAL_ITEM.get());
        var result = denied.getItem().useOn(new UseOnContext(level, machine, InteractionHand.MAIN_HAND, denied, hit));
        helper.assertValueEqual(InteractionResult.FAIL, result, "a fake player must not place a Security Terminal");
        helper.assertTrue(denied.getCount() == 1, "the denied terminal item is not consumed");
        helper.assertTrue(PartHelper.getPart(level, pos, Direction.UP) == null, "no terminal part may exist");

        var other = AEParts.TERMINAL.stack();
        var below = new BlockHitResult(Vec3.atCenterOf(pos).add(0, -.49, 0), Direction.DOWN, pos, false);
        var otherResult = other.getItem().useOn(new UseOnContext(level, machine, InteractionHand.MAIN_HAND, other, below));
        helper.assertTrue(otherResult.consumesAction(),
                "other parts placed by fake players keep their existing behaviour, got " + otherResult);
        helper.assertTrue(PartHelper.getPart(level, pos, Direction.DOWN) != null, "ordinary terminal must be installed");

        var allowed = new ItemStack(MESecurity.TERMINAL_ITEM.get());
        result = allowed.getItem().useOn(new UseOnContext(level, real, InteractionHand.MAIN_HAND, allowed, hit));
        helper.assertTrue(result.consumesAction(), "a real player can place a Security Terminal");
        helper.assertTrue(PartHelper.getPart(level, pos, Direction.UP) != null, "real player's terminal must exist");
        helper.succeed();
    }
}
