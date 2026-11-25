package dev.ae2security.security;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.IFacadeItem;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.core.AppEng;
import appeng.items.materials.UpgradeCardItem;
import appeng.items.tools.powered.ColorApplicatorItem;
import appeng.me.cluster.IAEMultiBlock;
import appeng.me.helpers.IGridConnectedBlockEntity;
import appeng.parts.AEBasePart;
import appeng.util.InteractionUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Shared membership resolution for native AE2 hardware, including non-exposed nodes. */
public final class SecurityHardware {
    private SecurityHardware() {}

    static final class Networks {
        final Set<IGrid> grids = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean unresolved;

        void add(IGridNode node) {
            if (node == null) unresolved = true;
            else grids.add(node.getGrid());
        }

        boolean protectedNetwork() {
            return unresolved || grids.stream().anyMatch(grid -> !grid.getService(ISecurityGrid.class).terminalIds().isEmpty());
        }
    }

    public static boolean nativeHost(BlockEntity host) {
        return host != null && BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(host.getType()).getNamespace().equals("ae2")
                && (host instanceof IGridConnectedBlockEntity || host instanceof CableBusBlockEntity);
    }

    static Networks networks(BlockEntity host) {
        var result = new Networks();
        collect(host, result, Collections.newSetFromMap(new IdentityHashMap<>()));
        return result;
    }

    private static void collect(BlockEntity host, Networks result, Set<BlockEntity> visited) {
        if (!nativeHost(host) || !visited.add(host)) return;
        if (host instanceof CableBusBlockEntity cableBus) {
            var center = cableBus.getPart(null);
            if (center != null) collect(center, result);
            for (var side : Direction.values()) {
                var part = cableBus.getPart(side);
                if (part != null) collect(part, result);
            }
        } else if (host instanceof IGridConnectedBlockEntity connected) {
            result.add(connected.getMainNode().getNode());
            for (var side : Direction.values()) {
                var node = connected.getGridNode(side);
                if (node != null) result.add(node);
            }
        }
        if (host instanceof IAEMultiBlock<?> multiblock && multiblock.getCluster() != null) {
            var members = multiblock.getCluster().getBlockEntities();
            while (members.hasNext()) collect(members.next(), result, visited);
        }
    }

    static void collect(IPart part, Networks result) {
        // A missing node is only unresolved for parts that own one; a Cable Anchor never does.
        if (!nodeless(part)) result.add(part.getGridNode());
        var external = part.getExternalFacingNode();
        if (external != null) result.add(external);
    }

    /** AE2's Cable Anchor implements IPart directly and has no grid node at any time. */
    static boolean nodeless(IPart part) {
        return !(part instanceof AEBasePart) && part.getGridNode() == null;
    }

    /** A node-less part belongs to the cable it sits on, exactly like a facade. */
    static void collect(IPart part, IPartHost host, Networks result) {
        collect(part, result);
        var center = host.getPart(null);
        if (center != null && center != part && nodeless(part)) collect(center, result);
    }

    static boolean allowBuild(Networks networks, Player player) {
        if (player.level().isClientSide()) return true;
        if (networks.unresolved) {
            SecurityAccess.notice(player, AccessDecision.UNRESOLVED);
            return false;
        }
        for (var grid : networks.grids) {
            if (!SecurityAccess.allow(grid, player, Permission.BUILD, false)) return false;
        }
        return true;
    }

    public static boolean allowBuild(BlockEntity host, Player player) {
        return allowBuild(networks(host), player);
    }

    public static boolean allowBuild(IPart part, Player player) {
        var networks = new Networks();
        collect(part, networks);
        return allowBuild(networks, player);
    }

    public static boolean loaded(Level level, BlockPos pos) {
        return level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    public static boolean protectedBlock(Level level, BlockPos pos) {
        return loaded(level, pos) && networks(level.getBlockEntity(pos)).protectedNetwork();
    }

    /** Capability wrappers also reject stale references after unload or block replacement. */
    public static boolean cellAutomationAllowed(BlockEntity host) {
        var level = host.getLevel();
        return level != null && !level.isClientSide() && !host.isRemoved() && loaded(level, host.getBlockPos())
                && level.getBlockEntity(host.getBlockPos()) == host && !networks(host).protectedNetwork();
    }

    /** Runs before AE2's own tool-use event, including multipart memory-card operations. */
    public static void toolUse(PlayerInteractEvent.RightClickBlock event) {
        var player = event.getEntity();
        if (event.isCanceled() || event.getLevel().isClientSide()) return;
        var stack = event.getItemStack();
        // Sneak-using an upgrade card installs it directly, without opening a menu.
        boolean upgrade = stack.getItem() instanceof UpgradeCardItem && InteractionUtil.isInAlternateUseMode(player);
        if (!(upgrade || stack.getItem() instanceof IMemoryCard || stack.getItem() instanceof IFacadeItem
                || stack.getItem() instanceof ColorApplicatorItem
                || InteractionUtil.canWrenchRotate(stack) || InteractionUtil.canWrenchDisassemble(stack))) return;
        if (!loaded(event.getLevel(), event.getPos())) return;
        var host = event.getLevel().getBlockEntity(event.getPos());
        boolean allowed;
        if (host instanceof CableBusBlockEntity cableBus
                && InteractionUtil.isInAlternateUseMode(player) && InteractionUtil.canWrenchDisassemble(stack)) {
            // Owners must be able to remove their own terminal to recover a conflicting grid.
            allowed = SecurityBreak.allowWrench(cableBus, player, event.getHitVec());
        } else if (host instanceof CableBusBlockEntity cableBus) {
            // Selection must use the same facade visibility and player context as AE2.
            AppEng.instance().setPartInteractionPlayer(player);
            appeng.api.parts.SelectedPart selected;
            try {
                selected = cableBus.getCableBus().selectPartWorld(event.getHitVec().getLocation());
            } finally {
                AppEng.instance().setPartInteractionPlayer(null);
            }
            if (selected.part != null) {
                var networks = new Networks();
                collect(selected.part, cableBus, networks);
                allowed = allowBuild(networks, player);
            } else {
                allowed = SecurityBreak.allowWrench(cableBus, player, event.getHitVec());
            }
        } else {
            allowed = allowBuild(host, player);
        }
        if (!allowed) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            SecurityPlacement.resyncPosition(player, event.getPos());
        }
    }
}
