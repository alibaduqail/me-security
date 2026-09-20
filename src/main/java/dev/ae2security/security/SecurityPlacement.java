package dev.ae2security.security;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.UUID;

import appeng.api.implementations.parts.ICablePart;
import appeng.api.networking.IGrid;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.parts.PartPlacement;
import appeng.parts.misc.ToggleBusPart;
import appeng.parts.networking.CablePart;
import appeng.parts.networking.QuartzFiberPart;
import appeng.parts.p2p.MEP2PTunnelPart;
import appeng.api.util.AEColor;
import dev.ae2security.MESecurity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Authorizes player-driven AE2 multipart placement before AE2 changes the world. */
public final class SecurityPlacement {
    private SecurityPlacement() {}

    public static boolean allow(UseOnContext context) {
        var level = context.getLevel();
        var player = context.getPlayer();
        if (level.isClientSide() || player == null) return true;

        var stack = context.getItemInHand();
        if (!(stack.getItem() instanceof IPartItem<?> partItem)) return true;
        var placement = PartPlacement.getPartPlacement(player, level, stack, context.getClickedPos(),
                context.getClickedFace(), context.getClickLocation());
        if (placement == null) return true; // Let AE2 report its normal placement failure.

        var grids = newGridSet();
        var host = PartHelper.getPartHost(level, placement.pos());
        var part = partItem.createPart();
        if (part instanceof ICablePart cable) {
            // A new center joins all internal parts, but only exposed, color-compatible neighbors.
            if (host != null) {
                for (var side : Direction.values()) collectPartGrid(host, side, grids);
            }
            collectCableNeighbors(level, placement.pos(), host, cable.getCableColor(), grids);
        } else {
            if (host != null) {
                // Occupying a cable face can sever its existing external connection.
                collectPartGrid(host, null, grids);
            }
            // Only these AE2 parts expose an outward grid node. Ordinary terminals and
            // storage buses do not join the grid of the block they happen to face.
            if (part instanceof ToggleBusPart) {
                collectNeighbor(level, placement.pos(), placement.side(), AEColor.TRANSPARENT, grids);
            } else if (part instanceof MEP2PTunnelPart || part instanceof QuartzFiberPart) {
                var outward = newGridSet();
                collectNeighbor(level, placement.pos(), placement.side(), AEColor.TRANSPARENT, outward);
                // These have separate internal/external grids; they do not merge those policies.
                if (!allowBuild(outward, player)) return false;
            }
        }

        return allowConnections(grids, player, stack.getItem() == MESecurity.TERMINAL_ITEM.get());
    }

    public static boolean allowRecolor(CablePart cable, AEColor color, Player player) {
        if (cable.isClientSide() || player == null || cable.getCableColor() == color) return true;
        var grids = newGridSet();
        collectNode(cable.getGridNode(), grids);
        collectCableNeighbors(cable.getLevel(), cable.getBlockEntity().getBlockPos(), cable.getHost(), color, grids);
        return allowConnections(grids, player, false);
    }

    private static boolean allowConnections(Set<IGrid> grids, Player player, boolean placingSecurityTerminal) {
        var terminalIds = new java.util.HashSet<UUID>();
        for (var grid : grids) terminalIds.addAll(grid.getService(ISecurityGrid.class).terminalIds());

        if (terminalIds.size() > 1 || placingSecurityTerminal && !terminalIds.isEmpty()) {
            SecurityAccess.notice(player, Component.translatable("message.ae2security.one_terminal"));
            return false;
        }

        return allowBuild(grids, player);
    }

    /** Undo the client's predicted part/host when authorization rejects a use-on action. */
    public static void resyncDeniedPlacement(UseOnContext context) {
        if (!(context.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player)) return;
        var placement = PartPlacement.getPartPlacement(player, context.getLevel(), context.getItemInHand(),
                context.getClickedPos(), context.getClickedFace(), context.getClickLocation());
        resyncPosition(player, context.getClickedPos());
        if (placement != null && !placement.pos().equals(context.getClickedPos())) {
            resyncPosition(player, placement.pos());
        }
    }

    private static void resyncPosition(net.minecraft.server.level.ServerPlayer player, BlockPos pos) {
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(player.level(), pos));
        appeng.util.Platform.sendImmediateBlockEntityUpdate(player, pos);
    }

    private static boolean allowBuild(Set<IGrid> grids, Player player) {
        for (var grid : grids) {
            if (grid.getService(ISecurityGrid.class).terminalIds().isEmpty()) continue;
            var decision = SecurityAccess.check(grid, player, Permission.BUILD, false);
            if (decision != AccessDecision.ALLOW) {
                SecurityAccess.notice(player, decision == AccessDecision.DENIED
                        ? Component.translatable("message.ae2security.build_denied")
                        : Component.translatable(decision.translationKey()));
                return false;
            }
        }
        return true;
    }

    private static Set<IGrid> newGridSet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private static void collectCableNeighbors(Level level, BlockPos position, IPartHost host,
            AEColor color, Set<IGrid> grids) {
        for (var direction : Direction.values()) {
            if (host == null || host.getPart(direction) == null && !host.isBlocked(direction)) {
                collectNeighbor(level, position, direction, color, grids);
            }
        }
    }

    private static void collectNeighbor(Level level, BlockPos position, Direction direction,
            AEColor color, Set<IGrid> grids) {
        var neighbor = position.relative(direction);
        if (!level.getChunkSource().hasChunk(neighbor.getX() >> 4, neighbor.getZ() >> 4)) return;
        var node = GridHelper.getExposedNode(level, neighbor, direction.getOpposite());
        if (node != null && (color == AEColor.TRANSPARENT || node.getGridColor() == AEColor.TRANSPARENT
                || color == node.getGridColor())) collectNode(node, grids);
    }

    private static void collectPartGrid(IPartHost host, Direction side, Set<IGrid> grids) {
        var part = host.getPart(side);
        if (part != null) collectNode(part.getGridNode(), grids);
    }

    private static void collectNode(IGridNode node, Set<IGrid> grids) {
        if (node != null) grids.add(node.getGrid());
    }
}
