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
import appeng.parts.automation.AnnihilationPlanePart;
import appeng.parts.misc.ToggleBusPart;
import appeng.parts.networking.CablePart;
import appeng.parts.networking.QuartzFiberPart;
import appeng.parts.p2p.MEP2PTunnelPart;
import appeng.parts.storagebus.StorageBusPart;
import appeng.api.util.AEColor;
import appeng.api.orientation.BlockOrientation;
import appeng.block.AEBaseEntityBlock;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.blockentity.qnb.QuantumBridgeBlockEntity;
import appeng.blockentity.spatial.SpatialPylonBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.me.cluster.MBCalculator;
import appeng.me.cluster.implementations.CraftingCPUCalculator;
import appeng.me.cluster.implementations.QuantumCalculator;
import appeng.me.cluster.implementations.SpatialPylonCalculator;
import appeng.me.helpers.IGridConnectedBlockEntity;
import dev.ae2security.MESecurity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;

/** Authorizes player-driven AE2 multipart placement before AE2 changes the world. */
public final class SecurityPlacement {
    private SecurityPlacement() {}

    public static boolean allow(UseOnContext context) {
        var level = context.getLevel();
        var player = context.getPlayer();
        if (level.isClientSide() || player == null) return true;

        var stack = context.getItemInHand();
        if (!(stack.getItem() instanceof IPartItem<?> partItem)) return true;
        // A machine (e.g. a deployer) would become the terminal's owner, and no real player could manage it.
        if (stack.getItem() == MESecurity.TERMINAL_ITEM.get() && player instanceof FakePlayer) {
            SecurityAccess.notice(player, Component.translatable("message.ae2security.fake_player_terminal"));
            return false;
        }
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
            // A storage bus mounts the storage of the block it faces and an annihilation plane
            // destroys it, so both act on that hardware without ever joining its grid.
            if ((part instanceof StorageBusPart || part instanceof AnnihilationPlanePart)
                    && !allowFacedHardware(level, placement.pos().relative(placement.side()), player)) return false;
            // Only these AE2 parts expose an outward grid node. Ordinary terminals do not
            // join the grid of the block they happen to face.
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

    private static boolean allowFacedHardware(Level level, BlockPos faced, Player player) {
        if (!SecurityHardware.loaded(level, faced)) return true;
        var networks = SecurityHardware.networks(level.getBlockEntity(faced));
        if (networks.unresolved) {
            SecurityAccess.notice(player, AccessDecision.UNRESOLVED);
            return false;
        }
        return allowBuild(networks.grids, player);
    }

    /** Removing a side part re-exposes the center cable there, which is a new cable connection. */
    static boolean allowExposedSide(IPartHost host, Direction side, Player player) {
        var level = host.getBlockEntity().getLevel();
        if (level == null || level.isClientSide() || !(host.getPart(null) instanceof ICablePart cable)
                || host.isBlocked(side)) return true;
        var neighbor = newGridSet();
        collectNeighbor(level, host.getBlockEntity().getBlockPos(), side, cable.getCableColor(), neighbor);
        if (neighbor.isEmpty()) return true;
        collectNode(cable.getGridNode(), neighbor);
        return allowConnections(neighbor, player, false);
    }

    public static boolean allowRecolor(CablePart cable, AEColor color, Player player) {
        if (cable.isClientSide() || player == null || cable.getCableColor() == color) return true;
        var grids = newGridSet();
        collectNode(cable.getGridNode(), grids);
        collectCableNeighbors(cable.getLevel(), cable.getBlockEntity().getBlockPos(), cable.getHost(), color, grids);
        return allowConnections(grids, player, false);
    }

    /** P2P attunement replaces a part without going through normal item placement. */
    public static boolean allowReplacement(IPartHost host, IPartItem<?> item, Direction side, Player player) {
        var level = host.getBlockEntity().getLevel();
        if (level.isClientSide() || player == null) return true;
        var networks = new SecurityHardware.Networks();
        var old = host.getPart(side);
        if (old != null) SecurityHardware.collect(old, networks);
        if (side != null && MEP2PTunnelPart.class.isAssignableFrom(item.getPartClass())) {
            collectNeighbor(level, host.getBlockEntity().getBlockPos(), side, AEColor.TRANSPARENT, networks);
        }
        return SecurityHardware.allowBuild(networks, player);
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

    public static void resyncPosition(Player actor, BlockPos pos) {
        if (!(actor instanceof net.minecraft.server.level.ServerPlayer player) || !SecurityHardware.loaded(player.level(), pos)) return;
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

    /** Preflight the final state before placement, rotation or an in-place crafting-unit upgrade. */
    public static boolean allowStateChange(Level level, BlockPos pos, BlockState state, Player player) {
        if (level.isClientSide() || player == null) return true;
        if (!SecurityHardware.loaded(level, pos)) return false;
        var current = level.getBlockEntity(pos);
        var next = state.getBlock() instanceof AEBaseEntityBlock<?> block ? block.newBlockEntity(pos, state) : null;
        if (!SecurityHardware.nativeHost(current) && !SecurityHardware.nativeHost(next)) return true;
        if (!SecurityBreak.allowBlockBreak(current, player)) return deny(player, pos);
        var networks = SecurityHardware.networks(current);
        if (next instanceof IGridConnectedBlockEntity connected && calculator(next) == null) {
            for (var side : connected.getGridConnectableSides(BlockOrientation.get(state))) {
                collectNeighbor(level, pos, side, AEColor.TRANSPARENT, networks);
            }
        }

        var previews = new java.util.HashSet<PreviewBounds>();
        var seeds = new java.util.ArrayList<BlockPos>();
        seeds.add(pos);
        for (var side : Direction.values()) seeds.add(pos.relative(side));
        for (var seed : seeds) {
            var member = blockEntity(level, seed, pos, next);
            if (calculator(member) == null || !sameMultiblock(member, current) && !sameMultiblock(member, next)) continue;
            var affected = SecurityHardware.networks(level.getBlockEntity(seed));
            networks.grids.addAll(affected.grids);
            networks.unresolved |= affected.unresolved;
            previewMultiblock(level, seed, pos, next, member, networks, previews);
        }
        if (!SecurityHardware.allowBuild(networks, player)
                || !allowConnections(networks.grids, player, false)) return deny(player, pos);
        return true;
    }

    private static boolean deny(Player player, BlockPos pos) {
        resyncPosition(player, pos);
        return false;
    }

    private static void collectNeighbor(Level level, BlockPos pos, Direction side, AEColor color,
            SecurityHardware.Networks networks) {
        var neighbor = pos.relative(side);
        if (!SecurityHardware.loaded(level, neighbor)) return;
        var node = GridHelper.getExposedNode(level, neighbor, side.getOpposite());
        if (node != null && (color == AEColor.TRANSPARENT || node.getGridColor() == AEColor.TRANSPARENT
                || color == node.getGridColor())) networks.add(node);
        else if (level.getBlockEntity(neighbor) instanceof IGridConnectedBlockEntity host
                && !host.getMainNode().isReady()) networks.unresolved = true;
    }

    private static BlockEntity blockEntity(Level level, BlockPos pos, BlockPos changed, BlockEntity next) {
        if (pos.equals(changed)) return next;
        return SecurityHardware.loaded(level, pos) ? level.getBlockEntity(pos) : null;
    }

    private static boolean sameMultiblock(BlockEntity a, BlockEntity b) {
        return a instanceof CraftingBlockEntity && b instanceof CraftingBlockEntity
                || a instanceof QuantumBridgeBlockEntity && b instanceof QuantumBridgeBlockEntity
                || a instanceof SpatialPylonBlockEntity && b instanceof SpatialPylonBlockEntity;
    }

    private static MBCalculator<?, ?> calculator(BlockEntity host) {
        if (host instanceof CraftingBlockEntity crafting) return new CraftingCPUCalculator(crafting);
        if (host instanceof QuantumBridgeBlockEntity quantum) return new QuantumCalculator(quantum);
        if (host instanceof SpatialPylonBlockEntity pylon) return new SpatialPylonCalculator(pylon);
        return null;
    }

    private record PreviewBounds(BlockPos min, BlockPos max, Class<?> family) {}

    // ponytail: cap one preview at 8192 probes; larger pylon lines need incremental validation.
    private static final int MAX_PREVIEW_PROBES = 8192;

    /** Read-only version of AE2 19.2.17's MBCalculator bounds/structure/exposed-face rules. */
    private static void previewMultiblock(Level level, BlockPos seed, BlockPos changed, BlockEntity next,
            BlockEntity member, SecurityHardware.Networks networks, Set<PreviewBounds> previews) {
        var min = seed.mutable();
        var max = seed.mutable();
        int probes = 0;
        for (var side : new Direction[]{Direction.WEST, Direction.DOWN, Direction.NORTH,
                Direction.EAST, Direction.UP, Direction.SOUTH}) {
            var edge = side.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? min : max;
            while (sameMultiblock(member, blockEntity(level, edge.relative(side), changed, next))) {
                if (++probes > MAX_PREVIEW_PROBES) { networks.unresolved = true; return; }
                edge.move(side);
            }
            if (!SecurityHardware.loaded(level, edge.relative(side))) { networks.unresolved = true; return; }
        }
        var bounds = new PreviewBounds(min.immutable(), max.immutable(), calculator(member).getClass());
        if (!previews.add(bounds) || !calculator(member).checkMultiblockScale(min, max)) return;
        long volume = (long) (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1);
        if (volume > MAX_PREVIEW_PROBES) { networks.unresolved = true; return; }
        boolean storage = false;
        var positions = new java.util.ArrayList<BlockPos>();
        for (var position : BlockPos.betweenClosed(min, max)) {
            var host = blockEntity(level, position, changed, next);
            if (!sameMultiblock(member, host)) return;
            // A detached preview has no level; CraftingBlockEntity.getUnitBlock() would report a bare unit.
            if (host.getBlockState().getBlock() instanceof appeng.block.crafting.AbstractCraftingUnitBlock<?> unit) {
                storage |= unit.type.getStorageBytes() > 0;
            }
            if (host instanceof QuantumBridgeBlockEntity) {
                boolean center = position.getX() == (min.getX() + max.getX()) / 2
                        && position.getY() == (min.getY() + max.getY()) / 2
                        && position.getZ() == (min.getZ() + max.getZ()) / 2;
                if (!host.getBlockState().is(center ? AEBlocks.QUANTUM_LINK.block() : AEBlocks.QUANTUM_RING.block())) return;
            }
            positions.add(position.immutable());
        }
        if (member instanceof CraftingBlockEntity && !storage) return;
        // A same-family member outside any face invalidates AE2's rectangular region.
        for (var position : positions) {
            for (var side : Direction.values()) {
                var neighbor = position.relative(side);
                if (!inside(neighbor, min, max) && sameMultiblock(member, blockEntity(level, neighbor, changed, next))) return;
            }
        }
        for (var position : positions) {
            int extremes = (min.getX() != max.getX() && (position.getX() == min.getX() || position.getX() == max.getX()) ? 1 : 0)
                    + (min.getY() != max.getY() && (position.getY() == min.getY() || position.getY() == max.getY()) ? 1 : 0)
                    + (min.getZ() != max.getZ() && (position.getZ() == min.getZ() || position.getZ() == max.getZ()) ? 1 : 0);
            // Quantum corners and the center expose only faces within the ring.
            if (member instanceof QuantumBridgeBlockEntity && extremes != 1) continue;
            for (var side : Direction.values()) {
                if (!inside(position.relative(side), min, max)) collectNeighbor(level, position, side, AEColor.TRANSPARENT, networks);
            }
        }
    }

    private static boolean inside(BlockPos pos, BlockPos min, BlockPos max) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX() && pos.getY() >= min.getY()
                && pos.getY() <= max.getY() && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }
}
