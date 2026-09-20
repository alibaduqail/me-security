package dev.ae2security.security;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import appeng.api.networking.IGrid;
import appeng.api.parts.IPart;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.core.AppEng;
import dev.ae2security.part.SecurityTerminalPart;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;

/** Authorizes player-driven removal of parts from a protected AE2 cable bus. */
public final class SecurityBreak {
    private SecurityBreak() {}

    public static boolean allowWrench(CableBusBlockEntity host, Player player, BlockHitResult hit) {
        if (player.level().isClientSide()) return true;

        // Match AE2's player-dependent facade selection (including cable-facade visibility).
        appeng.api.parts.SelectedPart selected;
        AppEng.instance().setPartInteractionPlayer(player);
        try {
            selected = host.getCableBus().selectPartWorld(hit.getLocation());
        } finally {
            AppEng.instance().setPartInteractionPlayer(null);
        }
        if (selected.part instanceof SecurityTerminalPart terminal) {
            return allowTerminalRemoval(terminal, player);
        }

        var grids = Collections.newSetFromMap(new IdentityHashMap<IGrid, Boolean>());
        if (selected.part != null) {
            addGrid(selected.part, grids);
        } else if (selected.facade != null && host.getPart(null) != null) {
            addGrid(host.getPart(null), grids);
        }
        return allowBuild(grids, player);
    }

    public static boolean allowBlockBreak(CableBusBlockEntity host, Player player) {
        if (player.level().isClientSide()) return true;

        for (var part : parts(host)) {
            if (part instanceof SecurityTerminalPart terminal && !isTerminalOwner(terminal, player)) {
                SecurityAccess.notice(player, Component.translatable("message.ae2security.terminal_break_owner"));
                return false;
            }
        }
        return allowBuild(grids(host), player);
    }

    private static boolean allowTerminalRemoval(SecurityTerminalPart terminal, Player player) {
        if (isTerminalOwner(terminal, player)) return true;
        SecurityAccess.notice(player, Component.translatable("message.ae2security.terminal_break_owner"));
        return false;
    }

    private static boolean isTerminalOwner(SecurityTerminalPart terminal, Player player) {
        var server = player.getServer();
        if (server == null) return false;
        var policy = SecuritySavedData.get(server).policy(terminal.terminalId());
        return policy != null && policy.isOwner(player.getUUID());
    }

    private static boolean allowBuild(Set<IGrid> grids, Player player) {
        for (var grid : grids) {
            if (grid.getService(ISecurityGrid.class).terminalIds().isEmpty()) continue;
            var decision = SecurityAccess.check(grid, player, Permission.BUILD, false);
            if (decision != AccessDecision.ALLOW) {
                SecurityAccess.notice(player, decision == AccessDecision.DENIED
                        ? Component.translatable("message.ae2security.break_denied")
                        : Component.translatable(decision.translationKey()));
                return false;
            }
        }
        return true;
    }

    private static Set<IGrid> grids(CableBusBlockEntity host) {
        var result = Collections.newSetFromMap(new IdentityHashMap<IGrid, Boolean>());
        for (var part : parts(host)) addGrid(part, result);
        return result;
    }

    private static Set<IPart> parts(CableBusBlockEntity host) {
        var result = Collections.newSetFromMap(new IdentityHashMap<IPart, Boolean>());
        var center = host.getPart(null);
        if (center != null) result.add(center);
        for (var side : Direction.values()) {
            var part = host.getPart(side);
            if (part != null) result.add(part);
        }
        return result;
    }

    private static void addGrid(IPart part, Set<IGrid> grids) {
        var node = part.getGridNode();
        if (node != null && node.getGrid() != null) grids.add(node.getGrid());
        var external = part.getExternalFacingNode();
        if (external != null && external.getGrid() != null) grids.add(external.getGrid());
    }
}
