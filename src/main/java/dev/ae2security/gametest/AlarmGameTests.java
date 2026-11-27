package dev.ae2security.gametest;

import java.util.UUID;

import appeng.api.parts.PartHelper;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEParts;
import com.mojang.authlib.GameProfile;
import dev.ae2security.MESecurity;
import dev.ae2security.menu.SecurityMenu;
import dev.ae2security.network.SetAlarm;
import dev.ae2security.part.SecurityTerminalPart;
import dev.ae2security.security.AlarmMode;
import dev.ae2security.security.Permission;
import dev.ae2security.security.SecurityAccess;
import dev.ae2security.security.SecurityAlarm;
import dev.ae2security.security.SecurityBreak;
import dev.ae2security.security.SecuritySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The intrusion alarm on a real, placed Security Terminal: who raises it, what it emits and who is told. */
@GameTestHolder(MESecurity.ID)
@PrefixGameTestTemplate(false)
public final class AlarmGameTests {
    /** Every fixture stays within 0..4 on each axis so neighboring tests can never join its networks. */
    private static final BlockPos CENTER = new BlockPos(2, 1, 2);

    private AlarmGameTests() {}

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 200)
    public static void onlyAStrangerRaisesTheRedstoneAlarmAndItEndsByItself(GameTestHelper helper) {
        var level = helper.getLevel();
        var position = helper.absolutePos(CENTER);
        var lamp = position.above();
        var owner = player(level, "AlarmOwner", position);
        var trusted = player(level, "AlarmTrusted", position);
        var terminal = terminal(level, position, owner);
        level.setBlockAndUpdate(lamp, Blocks.REDSTONE_LAMP.defaultBlockState());
        // A part joins the world, and gets its policy, on the first tick after its cable bus is placed.
        helper.runAfterDelay(5, () -> {
            var data = SecuritySavedData.get(level.getServer());
            var id = terminal.terminalId();
            var grid = terminal.getGridNode().getGrid();
            data.update(id, data.policy(id).trust(owner.getUUID(), trusted.getUUID(), Permission.VIEW.bit()));

            helper.assertFalse(SecurityAccess.allow(grid, player(level, "AlarmFirst", position), Permission.VIEW, false),
                    "precondition: a stranger is denied");
            helper.assertFalse(SecurityAlarm.pulsing(terminal), "redstone is off until the owner switches it on");
            helper.assertFalse(lit(level, lamp), "the default mode powers nothing");

            data.setAlarmMode(id, AlarmMode.REDSTONE);
            helper.assertFalse(SecurityAccess.allow(grid, trusted, Permission.BUILD, false),
                    "precondition: a trusted player without Build is denied");
            helper.assertTrue(SecurityAccess.allow(grid, owner, Permission.BUILD, false), "precondition: the owner is allowed");
            var stranger = player(level, "AlarmStranger", position);
            helper.assertFalse(SecurityAlarm.quietly(() -> SecurityAccess.allow(grid, stranger, Permission.VIEW, false)),
                    "precondition: a re-validation is denied too");
            helper.assertFalse(SecurityAlarm.pulsing(terminal),
                    "a trusted player lacking a permission and a re-validated open menu are not intrusions");

            helper.assertFalse(SecurityAccess.allow(grid, stranger, Permission.EXTRACT, false), "the stranger is denied");
            helper.assertTrue(SecurityAlarm.pulsing(terminal), "a stranger's denied attempt starts the pulse");
            for (var side : Direction.values()) {
                helper.assertValueEqual(15, level.getSignal(position, side), "signal towards " + side);
            }
            // AE2 updates a cable bus's neighbors on the next tick, never in the middle of the denied action.
            helper.runAfterDelay(3, () -> {
                helper.assertTrue(lit(level, lamp), "a lamp beside the terminal's block lights");
                helper.runAfterDelay(SecurityAlarm.PULSE_TICKS + 10, () -> {
                    helper.assertFalse(SecurityAlarm.pulsing(terminal), "the pulse ends by itself");
                    helper.assertValueEqual(0, level.getSignal(position, Direction.DOWN), "signal after the pulse");
                    helper.assertFalse(lit(level, lamp), "the lamp goes out again");
                    data.retire(id);
                    level.destroyBlock(lamp, false);
                    level.destroyBlock(position, false);
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void tamperingWithTheTerminalItselfRaisesTheAlarmOnlyForStrangers(GameTestHelper helper) {
        var level = helper.getLevel();
        var position = helper.absolutePos(CENTER);
        var owner = player(level, "TamperOwner", position);
        var builder = player(level, "TamperBuilder", position);
        var stranger = player(level, "TamperStranger", position);
        var terminal = terminal(level, position, owner);
        helper.runAfterDelay(5, () -> {
            var data = SecuritySavedData.get(level.getServer());
            var id = terminal.terminalId();
            data.update(id, data.policy(id).trust(owner.getUUID(), builder.getUUID(), Permission.BUILD.bit()));
            data.setAlarmMode(id, AlarmMode.REDSTONE);
            var host = level.getBlockEntity(position);

            helper.assertFalse(SecurityBreak.allowBlockBreak(host, builder), "precondition: only the owner breaks it");
            terminal.onUseWithoutItem(builder, Vec3.atCenterOf(position));
            helper.assertFalse(SecurityAlarm.pulsing(terminal), "a trusted player at the terminal is not an intruder");

            terminal.onUseWithoutItem(stranger, Vec3.atCenterOf(position));
            helper.assertTrue(SecurityAlarm.pulsing(terminal), "a stranger trying to open the owner's screen is");

            data.retire(id);
            level.destroyBlock(position, false);
            helper.assertFalse(SecurityAlarm.pulsing(terminal), "a removed terminal stops emitting");

            var second = terminal(level, position, owner);
            helper.runAfterDelay(5, () -> {
                data.setAlarmMode(second.terminalId(), AlarmMode.REDSTONE);
                helper.assertFalse(SecurityBreak.allowBlockBreak(level.getBlockEntity(position), stranger),
                        "precondition: a stranger cannot break the terminal");
                helper.assertTrue(SecurityAlarm.pulsing(second), "a stranger trying to break the terminal is an intruder");
                data.retire(second.terminalId());
                level.destroyBlock(position, false);
                helper.succeed();
            });
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void anAbsentOwnerIsToldAtLoginAndAPresentOneAtOnce(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        var position = helper.absolutePos(CENTER);
        var owner = player(level, "DigestOwner", position);
        var terminal = terminal(level, position, owner);
        helper.runAfterDelay(5, () -> {
            var data = SecuritySavedData.get(server);
            var id = terminal.terminalId();
            var grid = terminal.getGridNode().getGrid();

            var repeated = player(level, "DigestRepeat", position);
            SecurityAccess.allow(grid, repeated, Permission.VIEW, false);
            SecurityAccess.allow(grid, repeated, Permission.EXTRACT, false);
            SecurityAccess.allow(grid, player(level, "DigestOther", position), Permission.BUILD, false);
            helper.assertTrue(SecurityAlarm.digest(server, repeated.getUUID()).isEmpty(), "only the owner is told");
            var lines = SecurityAlarm.digest(server, owner.getUUID());
            helper.assertValueEqual(1, lines.size(), "one summary line for the terminal");
            helper.assertTrue(SecurityAlarm.digest(server, owner.getUUID()).isEmpty(), "the summary is given once");

            data.setAlarmMode(id, AlarmMode.OFF);
            SecurityAccess.allow(grid, player(level, "DigestMuted", position), Permission.VIEW, false);
            helper.assertTrue(SecurityAlarm.digest(server, owner.getUUID()).isEmpty(), "a switched-off alarm keeps nothing");

            // A real player in the server's list is told at once, so nothing is left for their next login.
            data.setAlarmMode(id, AlarmMode.ALERT);
            var present = helper.makeMockServerPlayerInLevel();
            try {
                data.update(id, data.policy(id).trust(owner.getUUID(), present.getUUID(), Permission.VIEW.bit())
                        .transfer(owner.getUUID(), present.getUUID()));
                SecurityAccess.allow(grid, player(level, "DigestLive", position), Permission.VIEW, false);
                helper.assertTrue(SecurityAlarm.digest(server, present.getUUID()).isEmpty(),
                        "an alert delivered live is not repeated at login");
            } finally {
                // Leave the broadcast list again, or concurrent tests send AE2 packets to the mock.
                server.getPlayerList().remove(present);
            }
            SecuritySavedData.get(server).retire(id);
            level.destroyBlock(position, false);
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void onlyTheOwnerSwitchesTheAlarmMode(GameTestHelper helper) {
        var level = helper.getLevel();
        var position = helper.absolutePos(CENTER);
        var owner = player(level, "ModeOwner", position);
        var stranger = player(level, "ModeStranger", position);
        var terminal = terminal(level, position, owner);
        helper.runAfterDelay(5, () -> {
            var data = SecuritySavedData.get(level.getServer());
            var id = terminal.terminalId();
            helper.assertValueEqual(AlarmMode.ALERT, data.alarmMode(id), "a new terminal alerts its owner");

            var forged = new SecurityMenu(10, stranger.getInventory(), terminal);
            stranger.containerMenu = forged;
            forged.setAlarm(stranger, new SetAlarm(10, AlarmMode.OFF));
            helper.assertValueEqual(AlarmMode.ALERT, data.alarmMode(id), "a menu a non-owner forged changes nothing");
            stranger.containerMenu = stranger.inventoryMenu;

            var menu = new SecurityMenu(9, owner.getInventory(), terminal);
            owner.containerMenu = menu;
            menu.setAlarm(stranger, new SetAlarm(9, AlarmMode.OFF));
            menu.setAlarm(owner, new SetAlarm(8, AlarmMode.OFF));
            helper.assertValueEqual(AlarmMode.ALERT, data.alarmMode(id), "another player or another menu id is ignored");
            menu.setAlarm(owner, new SetAlarm(9, AlarmMode.REDSTONE));
            helper.assertValueEqual(AlarmMode.REDSTONE, data.alarmMode(id), "the owner switches the mode");
            owner.containerMenu = owner.inventoryMenu;

            data.retire(id);
            helper.assertValueEqual(AlarmMode.DEFAULT, data.alarmMode(id), "a retired terminal keeps no setting");
            level.destroyBlock(position, false);
            helper.succeed();
        });
    }

    private static boolean lit(ServerLevel level, BlockPos lamp) {
        return level.getBlockState(lamp).getValue(RedstoneLampBlock.LIT);
    }

    private static ServerPlayer player(ServerLevel level, String name, BlockPos near) {
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), name));
        player.setPos(Vec3.atCenterOf(near));
        return player;
    }

    private static SecurityTerminalPart terminal(ServerLevel level, BlockPos position, ServerPlayer owner) {
        var cable = PartHelper.setPart(level, position, null, owner, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        if (cable == null) throw new IllegalStateException("Could not place GameTest cable");
        var part = PartHelper.setPart(level, position, Direction.NORTH, owner, MESecurity.TERMINAL_ITEM.get());
        if (!(part instanceof SecurityTerminalPart terminal)) {
            throw new IllegalStateException("Could not place GameTest terminal");
        }
        return terminal;
    }
}
