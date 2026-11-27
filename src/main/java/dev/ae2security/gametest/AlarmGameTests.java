package dev.ae2security.gametest;

import java.util.List;
import java.util.UUID;

import appeng.api.parts.PartHelper;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.core.definitions.AEParts;
import com.mojang.authlib.GameProfile;
import dev.ae2security.MESecurity;
import dev.ae2security.part.SecurityEmitterPart;
import dev.ae2security.part.SecurityTerminalPart;
import dev.ae2security.security.EmitterTrigger;
import dev.ae2security.security.IntrusionAlerts;
import dev.ae2security.security.MenuAccess;
import dev.ae2security.security.Permission;
import dev.ae2security.security.SecurityAccess;
import dev.ae2security.security.SecurityAlarm;
import dev.ae2security.security.SecurityBreak;
import dev.ae2security.security.SecuritySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The intrusion alarm on a real, placed Security Terminal and Security Emitter: who triggers it, what the
 * emitter outputs and who an Intrusion Alert Card tells.
 */
@GameTestHolder(MESecurity.ID)
@PrefixGameTestTemplate(false)
public final class AlarmGameTests {
    /** Every fixture stays within 0..4 on each axis so neighboring tests can never join its networks. */
    private static final BlockPos CENTER = new BlockPos(2, 1, 2);
    /** A powered network needs a moment to boot before its emitter is active. */
    private static final int BOOT_TICKS = 20;

    private AlarmGameTests() {}

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 200)
    public static void anEmitterWatchingStrangersPulsesOnlyForThemAndEndsByItself(GameTestHelper helper) {
        var level = helper.getLevel();
        var position = helper.absolutePos(CENTER);
        var lamp = position.above();
        var owner = player(level, "EmitterOwner", position);
        var trusted = player(level, "EmitterTrusted", position);
        var terminal = terminal(level, position, owner);
        var emitter = poweredEmitter(level, position, owner);
        level.setBlockAndUpdate(lamp, Blocks.REDSTONE_LAMP.defaultBlockState());
        helper.runAfterDelay(BOOT_TICKS, () -> {
            var data = SecuritySavedData.get(level.getServer());
            var id = terminal.terminalId();
            var grid = terminal.getGridNode().getGrid();
            data.update(id, data.policy(id).trust(owner.getUUID(), trusted.getUUID(), Permission.VIEW.bit()));
            helper.assertTrue(emitter.isActive(), "precondition: the emitter is powered");
            helper.assertValueEqual(EmitterTrigger.STRANGERS, emitter.trigger(), "a new emitter watches strangers");
            helper.assertFalse(lit(level, lamp), "nothing is emitted before an action is denied");

            helper.assertFalse(SecurityAccess.allow(grid, trusted, Permission.BUILD, false),
                    "precondition: a trusted player without Build is denied");
            helper.assertTrue(SecurityAccess.allow(grid, owner, Permission.BUILD, false), "precondition: the owner is allowed");
            var stranger = player(level, "EmitterStranger", position);
            helper.assertFalse(SecurityAlarm.quietly(() -> SecurityAccess.allow(grid, stranger, Permission.VIEW, false)),
                    "precondition: a re-validation is denied too");
            helper.assertFalse(SecurityAlarm.pulsing(emitter),
                    "a trusted player lacking a permission and a re-validated open menu do not trigger it");

            helper.assertFalse(SecurityAccess.allow(grid, stranger, Permission.EXTRACT, false), "the stranger is denied");
            helper.assertTrue(SecurityAlarm.pulsing(emitter), "a stranger triggers it");
            helper.assertValueEqual(0, emitter.isProvidingStrongPower(),
                    "the output changes at the end of the tick, never in the middle of the denied action");
            helper.runAfterDelay(1, () -> {
                helper.assertValueEqual(15, emitter.isProvidingStrongPower(), "the emitter's output");
                helper.assertTrue(lit(level, lamp), "a lamp in front of the emitter lights");
                helper.assertFalse(SecurityAccess.allow(grid, stranger, Permission.EXTRACT, false), "the stranger tries again");
                helper.runAfterDelay(SecurityAlarm.PULSE_TICKS, () -> {
                    helper.assertFalse(SecurityAlarm.pulsing(emitter),
                            "the pulse lasts an observer's two ticks: an attempt during it does not stretch it");
                    helper.assertValueEqual(0, emitter.isProvidingStrongPower(), "the output after the pulse");
                    helper.assertFalse(SecurityAccess.allow(grid, stranger, Permission.EXTRACT, false), "a later attempt");
                    helper.assertTrue(SecurityAlarm.pulsing(emitter), "a later attempt gives a pulse of its own");
                    helper.runAfterDelay(SecurityAlarm.PULSE_TICKS + 10, () -> {
                        helper.assertFalse(SecurityAlarm.pulsing(emitter), "the pulse ends by itself");
                        helper.assertFalse(lit(level, lamp), "the lamp goes out again");
                        cleanUp(level, position, id);
                        level.destroyBlock(lamp, false);
                        helper.succeed();
                    });
                });
            });
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void anEmitterWatchingEveryoneReactsToTrustedPlayersAndCanBeInverted(GameTestHelper helper) {
        var level = helper.getLevel();
        var position = helper.absolutePos(CENTER);
        var owner = player(level, "EveryoneOwner", position);
        var trusted = player(level, "EveryoneTrusted", position);
        var terminal = terminal(level, position, owner);
        var emitter = poweredEmitter(level, position, owner);
        emitter.setTrigger(EmitterTrigger.EVERYONE);
        helper.runAfterDelay(BOOT_TICKS, () -> {
            var data = SecuritySavedData.get(level.getServer());
            var id = terminal.terminalId();
            var grid = terminal.getGridNode().getGrid();
            data.update(id, data.policy(id).trust(owner.getUUID(), trusted.getUUID(), Permission.VIEW.bit()));
            emitter.setInverted(true);
            helper.assertValueEqual(15, emitter.isProvidingStrongPower(), "an inverted emitter emits while all is quiet");

            helper.assertTrue(SecurityAccess.allow(grid, owner, Permission.BUILD, false), "precondition: the owner is allowed");
            helper.assertFalse(SecurityAlarm.pulsing(emitter), "an allowed action never triggers it");
            helper.assertFalse(SecurityAccess.allow(grid, trusted, Permission.BUILD, false),
                    "precondition: a trusted player without Build is denied");
            helper.assertTrue(SecurityAlarm.pulsing(emitter), "an emitter watching everyone reacts to a trusted player");
            helper.assertValueEqual(MenuAccess.OWNER, MenuAccess.of(MESecurity.id("security_emitter")),
                    "only the owner opens a Security Emitter's settings");
            helper.runAfterDelay(1, () -> {
                helper.assertValueEqual(0, emitter.isProvidingStrongPower(), "an inverted emitter goes dark when triggered");
                emitter.setInverted(false);
                helper.assertValueEqual(15, emitter.isProvidingStrongPower(), "switching back takes effect at once");
                cleanUp(level, position, id);
                helper.assertFalse(SecurityAlarm.pulsing(emitter), "a removed emitter is forgotten");
                helper.succeed();
            });
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void tamperingWithTheTerminalItselfTriggersAnEmitterOnlyForStrangers(GameTestHelper helper) {
        var level = helper.getLevel();
        var position = helper.absolutePos(CENTER);
        var owner = player(level, "TamperOwner", position);
        var builder = player(level, "TamperBuilder", position);
        var stranger = player(level, "TamperStranger", position);
        var terminal = terminal(level, position, owner);
        var emitter = emitter(level, position, owner);
        helper.runAfterDelay(5, () -> {
            var data = SecuritySavedData.get(level.getServer());
            var id = terminal.terminalId();
            data.update(id, data.policy(id).trust(owner.getUUID(), builder.getUUID(), Permission.BUILD.bit()));
            var host = level.getBlockEntity(position);

            helper.assertFalse(SecurityBreak.allowBlockBreak(host, builder), "precondition: only the owner breaks it");
            helper.assertFalse(SecurityAlarm.pulsing(emitter), "a trusted builder at the terminal is not an intruder");
            terminal.onUseWithoutItem(builder, Vec3.atCenterOf(position));
            helper.assertFalse(SecurityAlarm.pulsing(emitter), "nor is a trusted player opening the owner's screen");

            terminal.onUseWithoutItem(stranger, Vec3.atCenterOf(position));
            helper.assertTrue(SecurityAlarm.pulsing(emitter), "a stranger trying to open the owner's screen is");
            data.retire(id);
            level.destroyBlock(position, false);

            var second = terminal(level, position, owner);
            var secondEmitter = emitter(level, position, owner);
            helper.runAfterDelay(5, () -> {
                helper.assertFalse(SecurityBreak.allowBlockBreak(level.getBlockEntity(position), stranger),
                        "precondition: a stranger cannot break the terminal");
                helper.assertTrue(SecurityAlarm.pulsing(secondEmitter), "a stranger trying to break the terminal is an intruder");
                cleanUp(level, position, second.terminalId());
                helper.succeed();
            });
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void onlyPlayersWithStandingCarryingALinkedCardAreAlerted(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        var position = helper.absolutePos(CENTER);
        var accessPoint = position.above();
        var owner = player(level, "CardOwner", position);
        var trusted = player(level, "CardTrusted", position);
        var uncarded = player(level, "CardUncarded", position);
        var elsewhere = player(level, "CardElsewhere", position);
        var outsider = player(level, "CardOutsider", position);
        var stranger = player(level, "CardStranger", position);
        var terminal = terminal(level, position, owner);
        // An access point facing up joins the cable below it through its back.
        level.setBlockAndUpdate(accessPoint, AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.UP));
        helper.runAfterDelay(5, () -> {
            var data = SecuritySavedData.get(server);
            var id = terminal.terminalId();
            var grid = terminal.getGridNode().getGrid();
            var policy = data.policy(id);
            for (var member : List.of(trusted, uncarded, elsewhere)) {
                policy = policy.trust(owner.getUUID(), member.getUUID(), Permission.VIEW.bit());
            }
            data.update(id, policy);
            var linked = GlobalPos.of(level.dimension(), accessPoint);

            owner.getInventory().add(wirelessTerminal(AEItems.WIRELESS_TERMINAL.asItem(), linked, true));
            trusted.setItemInHand(InteractionHand.OFF_HAND,
                    wirelessTerminal(AEItems.WIRELESS_CRAFTING_TERMINAL.asItem(), linked, true));
            uncarded.getInventory().add(wirelessTerminal(AEItems.WIRELESS_TERMINAL.asItem(), linked, false));
            elsewhere.getInventory().add(wirelessTerminal(AEItems.WIRELESS_TERMINAL.asItem(),
                    GlobalPos.of(level.dimension(), position.east(64)), true));
            outsider.getInventory().add(wirelessTerminal(AEItems.WIRELESS_TERMINAL.asItem(), linked, true));
            stranger.getInventory().add(wirelessTerminal(AEItems.WIRELESS_TERMINAL.asItem(), linked, true));

            var everyone = List.of(owner, trusted, uncarded, elsewhere, outsider, stranger);
            helper.assertValueEqual(List.of(owner, trusted),
                    IntrusionAlerts.recipients(everyone, grid, data.policy(id), stranger.getUUID()),
                    "the owner and a trusted player carrying a card linked to this network, in the inventory or offhand");

            var full = wirelessTerminal(AEItems.WIRELESS_TERMINAL.asItem(), linked, true);
            var spare = AEItems.WIRELESS_TERMINAL.asItem().getUpgrades(full).addItems(MESecurity.ALERT_CARD.toStack());
            helper.assertFalse(spare.isEmpty(), "one card per terminal");

            // A real player in the server's list is sent the alert without failing the denied action.
            var present = helper.makeMockServerPlayerInLevel();
            try {
                data.update(id, data.policy(id).trust(owner.getUUID(), present.getUUID(), Permission.VIEW.bit()));
                present.getInventory().add(wirelessTerminal(AEItems.WIRELESS_TERMINAL.asItem(), linked, true));
                helper.assertValueEqual(List.of(present), IntrusionAlerts.recipients(List.of(present), grid,
                        data.policy(id), stranger.getUUID()), "precondition: the mock player is a recipient");
                helper.assertFalse(SecurityAccess.allow(grid, stranger, Permission.VIEW, false), "the stranger is denied");
            } finally {
                // Leave the broadcast list again, or concurrent tests send AE2 packets to the mock.
                server.getPlayerList().remove(present);
            }

            helper.assertTrue(IntrusionAlerts.recipients(everyone, null, data.policy(id), stranger.getUUID()).isEmpty(),
                    "nobody is told about a terminal that is on no network");
            data.retire(id);
            level.destroyBlock(accessPoint, false);
            level.destroyBlock(position, false);
            helper.succeed();
        });
    }

    private static ItemStack wirelessTerminal(WirelessTerminalItem item, GlobalPos link, boolean card) {
        var stack = new ItemStack(item);
        WirelessTerminalItem.LINKABLE_HANDLER.link(stack, link);
        if (card) item.getUpgrades(stack).addItems(MESecurity.ALERT_CARD.toStack());
        return stack;
    }

    private static boolean lit(ServerLevel level, BlockPos lamp) {
        return level.getBlockState(lamp).getValue(RedstoneLampBlock.LIT);
    }

    private static ServerPlayer player(ServerLevel level, String name, BlockPos near) {
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), name));
        player.setPos(Vec3.atCenterOf(near));
        return player;
    }

    /** An emitter facing up on the terminal's cable, so its signal reaches the block above. */
    private static SecurityEmitterPart emitter(ServerLevel level, BlockPos position, ServerPlayer owner) {
        var part = PartHelper.setPart(level, position, Direction.UP, owner, MESecurity.EMITTER_ITEM.get());
        if (!(part instanceof SecurityEmitterPart emitter)) throw new IllegalStateException("Could not place GameTest emitter");
        return emitter;
    }

    /** An emitter only outputs while its network is powered, so this one gets a creative energy cell below. */
    private static SecurityEmitterPart poweredEmitter(ServerLevel level, BlockPos position, ServerPlayer owner) {
        level.setBlockAndUpdate(position.below(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        return emitter(level, position, owner);
    }

    private static void cleanUp(ServerLevel level, BlockPos position, UUID terminal) {
        SecuritySavedData.get(level.getServer()).retire(terminal);
        level.destroyBlock(position, false);
        level.destroyBlock(position.below(), false);
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
