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
import net.minecraft.world.level.block.entity.BlockEntity;
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
        if (selected.part instanceof SecurityTerminalPart terminal && !retired(terminal, player)) {
            return allowTerminalRemoval(terminal, player);
        }

        var networks = new SecurityHardware.Networks();
        if (selected.part != null) {
            // A Cable Anchor has no node of its own; it is authorized through its cable.
            SecurityHardware.collect(selected.part, host, networks);
        } else if (selected.facade != null && host.getPart(null) != null) {
            SecurityHardware.collect(host.getPart(null), networks);
        }
        if (!SecurityHardware.allowBuild(networks, player)) return false;
        // Freeing a cable face lets the center cable connect to whatever that face exposes.
        return selected.part == null || selected.side == null
                || SecurityPlacement.allowExposedSide(host, selected.side, player);
    }

    public static boolean allowBlockBreak(BlockEntity blockEntity, Player player) {
        if (player.level().isClientSide()) return true;
        if (!(blockEntity instanceof CableBusBlockEntity host)) {
            return SecurityHardware.allowBuild(blockEntity, player);
        }

        for (var part : parts(host)) {
            if (part instanceof SecurityTerminalPart terminal && !retired(terminal, player)
                    && !isTerminalOwner(terminal, player)) {
                SecurityAccess.notice(player, Component.translatable("message.ae2security.terminal_break_owner"));
                return false;
            }
        }
        return SecurityHardware.allowBuild(host, player);
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

    /** A tombstoned identity protects nothing, so its leftover part is ordinary hardware. */
    private static boolean retired(SecurityTerminalPart terminal, Player player) {
        var server = player.getServer();
        return server != null && SecuritySavedData.get(server).retired(terminal.terminalId());
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

}
