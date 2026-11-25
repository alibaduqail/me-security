package dev.ae2security.gametest;

import appeng.api.parts.PartHelper;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEParts;
import dev.ae2security.MESecurity;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Only players who helped build a network may secure it, or bridge their protected grid into it. */
@GameTestHolder(MESecurity.ID)
@PrefixGameTestTemplate(false)
public final class ClaimGameTests {
    private static final BlockPos CENTER = new BlockPos(2, 2, 2);

    private ClaimGameTests() {}

    /**
     * A real (non-fake) ServerPlayer with a fresh profile and a mock connection, beside the fixture so it never
     * blocks placement. It is removed from the player list straight away: a connected player left in the world
     * would receive AE2 effect packets from concurrently running tests and fail them.
     */
    private static ServerPlayer player(GameTestHelper helper, BlockPos pos) {
        var player = helper.makeMockServerPlayerInLevel();
        helper.getLevel().getServer().getPlayerList().remove(player);
        player.setPos(Vec3.atCenterOf(pos.offset(0, 0, 3)));
        return player;
    }

    private static void cable(ServerLevel level, BlockPos pos, ServerPlayer owner) {
        if (PartHelper.setPart(level, pos, null, owner, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT)) == null) {
            throw new IllegalStateException("Could not place GameTest cable");
        }
    }

    private static InteractionResult use(ServerLevel level, ServerPlayer player, ItemStack stack, BlockPos pos,
            Direction face) {
        var hit = new BlockHitResult(Vec3.atCenterOf(pos).add(face.getStepX() * .49, face.getStepY() * .49,
                face.getStepZ() * .49), face, pos, false);
        return stack.getItem().useOn(new UseOnContext(level, player, InteractionHand.MAIN_HAND, stack, hit));
    }

    private static ItemStack terminalStack() {
        return MESecurity.TERMINAL_ITEM.get().getDefaultInstance();
    }

    private static ItemStack cableStack() {
        return AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT).asItem().getDefaultInstance();
    }

    private static boolean hasTerminal(ServerLevel level, BlockPos pos) {
        return PartHelper.getPart(level, pos, Direction.UP) != null;
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void strangerCannotSecureSomeoneElsesNetwork(GameTestHelper helper) {
        var level = helper.getLevel();
        var center = helper.absolutePos(CENTER);
        var builder = player(helper, center);
        var stranger = player(helper, center);
        cable(level, center, builder);
        cable(level, center.east(), builder);
        helper.runAfterDelay(20, () -> {
            var stack = terminalStack();
            helper.assertValueEqual(InteractionResult.FAIL, use(level, stranger, stack, center, Direction.UP),
                    "a stranger cannot secure a network they did not build");
            helper.assertTrue(stack.getCount() == 1 && !hasTerminal(level, center),
                    "a denied claim keeps the item and installs nothing");
            helper.assertTrue(use(level, builder, terminalStack(), center, Direction.UP).consumesAction()
                            && hasTerminal(level, center), "the builder may secure their own network");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void ownerlessNetworkCanBeSecuredByAnyone(GameTestHelper helper) {
        var level = helper.getLevel();
        var center = helper.absolutePos(CENTER);
        var anyone = player(helper, center);
        cable(level, center, null);
        cable(level, center.east(), null);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(use(level, anyone, terminalStack(), center, Direction.UP).consumesAction()
                            && hasTerminal(level, center), "a grid with no recorded owner has no one to lock out");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void coBuilderCanSecureSharedNetwork(GameTestHelper helper) {
        var level = helper.getLevel();
        var center = helper.absolutePos(CENTER);
        var first = player(helper, center);
        var second = player(helper, center);
        cable(level, center, first);
        cable(level, center.east(), second);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(use(level, second, terminalStack(), center, Direction.UP).consumesAction()
                            && hasTerminal(level, center), "owning any node of the network is enough to secure it");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void cableCannotBridgeOwnTerminalIntoForeignNetwork(GameTestHelper helper) {
        var level = helper.getLevel();
        var center = helper.absolutePos(CENTER);
        var mine = player(helper, center);
        var theirs = player(helper, center);
        cable(level, center, mine);
        PartHelper.setPart(level, center, Direction.UP, mine, MESecurity.TERMINAL_ITEM.get());
        cable(level, center.east(2), theirs);
        helper.runAfterDelay(20, () -> {
            var bridge = cableStack();
            helper.assertValueEqual(InteractionResult.FAIL, use(level, mine, bridge, center, Direction.EAST),
                    "bridging a protected grid into someone else's network is a claim");
            helper.assertTrue(bridge.getCount() == 1 && level.isEmptyBlock(center.east()),
                    "a denied bridge keeps the cable item and places nothing");
            helper.assertTrue(use(level, mine, cableStack(), center, Direction.WEST).consumesAction(),
                    "extending one's own protected network stays allowed");
            helper.succeed();
        });
    }
}
