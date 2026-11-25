package dev.ae2security.gametest;

import java.util.UUID;

import appeng.api.parts.IPart;
import appeng.api.parts.PartHelper;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEParts;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ae2security.MESecurity;
import dev.ae2security.part.SecurityTerminalPart;
import dev.ae2security.security.AccessDecision;
import dev.ae2security.security.ISecurityGrid;
import dev.ae2security.security.Permission;
import dev.ae2security.security.SecurityAccess;
import dev.ae2security.security.SecurityBreak;
import dev.ae2security.security.SecuritySavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The /mesecurity recovery commands and the ownerless-placement fallback. */
@GameTestHolder(MESecurity.ID)
@PrefixGameTestTemplate(false)
public final class AdminCommandGameTests {
    private static final BlockPos ORIGIN = BlockPos.ZERO;

    private AdminCommandGameTests() {}

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void setOwnerMovesAccessToTheNewOwner(GameTestHelper helper) {
        var level = helper.getLevel();
        var position = helper.absolutePos(ORIGIN);
        var oldOwner = player(level, "AdminOldOwner");
        // The run directory keeps the player directory between runs, so the name must be unique to this run.
        var newName = "AdminNew" + UUID.randomUUID().toString().substring(0, 8);
        var newOwner = player(level, newName);
        var trusted = UUID.randomUUID();
        var data = SecuritySavedData.get(level.getServer());
        data.rememberPlayer(newOwner.getUUID(), newName);
        var terminal = terminal(level, position, oldOwner);
        var id = terminal.terminalId();
        // A part joins the world, and gets its policy, on the first tick after its cable bus is placed.
        helper.runAfterDelay(5, () -> {
            data.update(id, data.policy(id).trust(oldOwner.getUUID(), trusted, Permission.EXTRACT.bit()));
            var security = security(terminal);
            helper.assertValueEqual(AccessDecision.ALLOW, security.check(oldOwner.getUUID(), Permission.BUILD, true),
                    "precondition: the placer owns the terminal");
            helper.assertValueEqual(AccessDecision.DENIED, security.check(newOwner.getUUID(), Permission.VIEW, false),
                    "precondition: the new owner has no access yet");

            helper.assertValueEqual(1, run(level, admin(level), "mesecurity info " + id), "info by UUID works");
            helper.assertValueEqual(0, run(level, admin(level), "mesecurity setowner " + id + " NoSuchPlayerHere"),
                    "an unknown player is rejected");
            helper.assertValueEqual(newOwner.getUUID().equals(data.policy(id).owner()), false,
                    "a rejected command changes nothing");
            helper.assertValueEqual(1, run(level, admin(level), "mesecurity setowner " + id + " " + newName.toLowerCase(java.util.Locale.ROOT)),
                    "player names resolve case-insensitively from the saved directory");

            helper.assertValueEqual(newOwner.getUUID(), data.policy(id).owner(), "the new owner is recorded");
            helper.assertValueEqual(AccessDecision.DENIED, security.check(oldOwner.getUUID(), Permission.VIEW, false),
                    "the old owner loses access");
            helper.assertValueEqual(AccessDecision.ALLOW, security.check(newOwner.getUUID(), Permission.BUILD, true),
                    "the new owner gains full access");
            helper.assertValueEqual(AccessDecision.ALLOW, security.check(trusted, Permission.EXTRACT, false),
                    "trusted entries remain after the transfer");
            helper.assertValueEqual(0, run(level, admin(level), "mesecurity setowner " + id + " " + newName),
                    "making the current owner the owner again is rejected");
            helper.assertValueEqual(-1, run(level, admin(level).withPermission(1), "mesecurity setowner " + id
                    + " AdminOldOwner"), "the command needs permission level 2");
            helper.assertValueEqual(newOwner.getUUID(), data.policy(id).owner(), "no change without permission");
            cleanUp(level, position, terminal);
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void retireMakesTheNetworkPublic(GameTestHelper helper) {
        var level = helper.getLevel();
        var position = helper.absolutePos(ORIGIN);
        var owner = player(level, "AdminRetireOwner");
        var guest = UUID.randomUUID();
        var terminal = terminal(level, position, owner);
        var id = terminal.terminalId();
        helper.runAfterDelay(5, () -> {
            var security = security(terminal);
            var data = SecuritySavedData.get(level.getServer());
            helper.assertValueEqual(AccessDecision.DENIED, security.check(guest, Permission.VIEW, false),
                    "precondition: the network is private");

            helper.assertValueEqual(1, run(level, admin(level), "mesecurity retire " + id), "retire succeeds");
            helper.assertTrue(data.retired(id) && data.policy(id) == null, "the identity is tombstoned");
            helper.assertValueEqual(AccessDecision.ALLOW, security.check(guest, Permission.EXTRACT, false),
                    "the network is unprotected");
            helper.assertTrue(security.terminalIds().isEmpty(), "a retired identity governs nothing");
            helper.assertValueEqual(0, run(level, admin(level), "mesecurity retire " + id),
                    "retiring twice is reported, not repeated");
            cleanUp(level, position, terminal);
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void bypassLetsAnOperatorThroughUntilToggledOff(GameTestHelper helper) {
        var level = helper.getLevel();
        var position = helper.absolutePos(ORIGIN);
        var owner = player(level, "AdminBypassOwner");
        var operator = player(level, "AdminBypassOperator");
        var terminal = terminal(level, position, owner);
        helper.runAfterDelay(5, () -> {
            var grid = terminal.getGridNode().getGrid();
            var host = level.getBlockEntity(position);
            try {
                helper.assertValueEqual(AccessDecision.DENIED,
                        SecurityAccess.check(grid, operator, Permission.BUILD, false), "precondition: not trusted");
                helper.assertFalse(SecurityBreak.allowBlockBreak(host, operator), "precondition: owner-only removal");

                helper.assertValueEqual(1, run(level, admin(level).withEntity(operator), "mesecurity bypass"),
                        "bypass toggles on");
                helper.assertTrue(SecurityAccess.bypassing(operator), "the operator is bypassing");
                helper.assertValueEqual(AccessDecision.ALLOW,
                        SecurityAccess.check(grid, operator, Permission.BUILD, false), "bypass passes Build");
                helper.assertValueEqual(AccessDecision.ALLOW,
                        SecurityAccess.check(grid, operator, Permission.EXTRACT, false), "bypass passes Extract");
                helper.assertTrue(SecurityBreak.allowBlockBreak(host, operator),
                        "bypass may remove the Security Terminal");
                helper.assertFalse(SecurityAccess.bypassing(owner), "bypass is per player");
                helper.assertFalse(SecuritySavedData.get(level.getServer()).policy(terminal.terminalId())
                        .isOwner(operator.getUUID()), "bypass does not make the operator the owner");

                helper.assertValueEqual(1, run(level, admin(level).withEntity(operator), "mesecurity bypass"),
                        "bypass toggles off");
                helper.assertValueEqual(AccessDecision.DENIED,
                        SecurityAccess.check(grid, operator, Permission.BUILD, false), "protection applies again");
                helper.assertFalse(SecurityBreak.allowBlockBreak(host, operator), "owner-only removal applies again");
                helper.assertValueEqual(0, run(level, admin(level), "mesecurity bypass"),
                        "bypass needs a player as its source");
            } finally {
                if (SecurityAccess.bypassing(operator)) run(level, admin(level).withEntity(operator), "mesecurity bypass");
            }
            cleanUp(level, position, terminal);
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void ownerlessFreshPlacementProtectsNothing(GameTestHelper helper) {
        var level = helper.getLevel();
        var position = helper.absolutePos(ORIGIN);
        var placer = player(level, "AdminOwnerlessCable");
        var cable = PartHelper.setPart(level, position, null, placer, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        helper.assertTrue(cable != null, "cable must be placeable");
        var terminal = PartHelper.setPart(level, position, Direction.NORTH, null, MESecurity.TERMINAL_ITEM.get());
        helper.assertTrue(terminal instanceof SecurityTerminalPart, "an ownerless terminal can still be placed");
        var id = ((SecurityTerminalPart) terminal).terminalId();
        var data = SecuritySavedData.get(level.getServer());
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(data.retired(id) && data.policy(id) == null, "the ownerless identity is retired");
            var security = cable.getGridNode().getGrid().getService(ISecurityGrid.class);
            helper.assertTrue(security.terminalIds().isEmpty(), "the network is public, not unresolved");
            helper.assertValueEqual(AccessDecision.ALLOW, security.check(UUID.randomUUID(), Permission.VIEW, false),
                    "everyone keeps access");
            cleanUp(level, position, (SecurityTerminalPart) terminal);
            helper.succeed();
        });
    }

    private static int run(ServerLevel level, CommandSourceStack source, String command) {
        try {
            return level.getServer().getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException e) {
            return -1;
        }
    }

    private static CommandSourceStack admin(ServerLevel level) {
        return level.getServer().createCommandSourceStack().withLevel(level);
    }

    private static ServerPlayer player(ServerLevel level, String name) {
        return FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), name));
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

    private static ISecurityGrid security(IPart part) {
        return part.getGridNode().getGrid().getService(ISecurityGrid.class);
    }

    private static void cleanUp(ServerLevel level, BlockPos position, SecurityTerminalPart terminal) {
        SecuritySavedData.get(level.getServer()).retire(terminal.terminalId());
        level.destroyBlock(position, false);
    }
}
