package dev.ae2security.security;

import java.util.List;
import java.util.UUID;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.events.GridSpatialEvent;
import appeng.me.helpers.IGridConnectedBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;

/** Vetoes world-driven mutations that have no acting player: mob griefing and Spatial IO. */
public final class SecurityWorldEvents {
    private SecurityWorldEvents() {}

    /** AE2's grid event bus is a plain map; call this from the main thread during common setup. */
    public static void registerGridEvents() {
        GridHelper.addEventHandler(GridSpatialEvent.class, SecurityWorldEvents::spatialTransition);
    }

    /** Withers, Ender Dragons and other block-breaking mobs destroy blocks without an explosion. */
    public static void livingDestroy(LivingDestroyBlockEvent event) {
        var level = event.getEntity().level();
        if (!level.isClientSide() && SecurityHardware.protectedBlock(level, event.getPos())) event.setCanceled(true);
    }

    static void spatialTransition(IGrid portGrid, GridSpatialEvent event) {
        if (!event.isTransitionPrevented() && !transitionAllowed(portGrid, event)) event.preventTransition();
    }

    /**
     * AE2 swaps the interior of the pylon bounding box in the port's own level. The box itself is that
     * interior grown by one block, which also covers hardware the deployed contents would end up touching.
     */
    private static boolean transitionAllowed(IGrid portGrid, GridSpatialEvent event) {
        var spatial = portGrid.getSpatialService();
        if (!(event.spatialIoLevel instanceof ServerLevel level) || !spatial.hasRegion()) return true;
        BlockPos min = spatial.getMin(), max = spatial.getMax();
        UUID mover = null;
        for (int chunkX = min.getX() >> 4; chunkX <= max.getX() >> 4; chunkX++) {
            for (int chunkZ = min.getZ() >> 4; chunkZ <= max.getZ() >> 4; chunkZ++) {
                // AE2 force-loads every chunk of the region before moving it, so an unloaded chunk is not exempt.
                // Hardware in a chunk loaded here has no grid node yet and is denied as unresolved.
                var chunk = level.getChunk(chunkX, chunkZ);
                for (var blockEntity : List.copyOf(chunk.getBlockEntities().values())) {
                    var pos = blockEntity.getBlockPos();
                    if (pos.getX() < min.getX() || pos.getX() > max.getX() || pos.getY() < min.getY()
                            || pos.getY() > max.getY() || pos.getZ() < min.getZ() || pos.getZ() > max.getZ()
                            || !SecurityHardware.gridHost(blockEntity)) continue;
                    var networks = SecurityHardware.networks(blockEntity);
                    if (networks.unresolved) return false;
                    for (var grid : networks.grids) {
                        // The port's own pylons, cables and machines sit on the edge of its own region.
                        if (grid == portGrid) continue;
                        var security = grid.getService(ISecurityGrid.class);
                        if (security.terminalIds().isEmpty()) continue;
                        if (mover == null && (mover = portOwner(event)) == null) return false;
                        if (security.check(mover, Permission.BUILD, false) != AccessDecision.ALLOW) return false;
                    }
                }
            }
        }
        return true;
    }

    /** A redstone-triggered transition acts for whoever placed the Spatial IO Port, as AE2's plot ownership does. */
    private static UUID portOwner(GridSpatialEvent event) {
        if (!(event.spatialIoLevel.getBlockEntity(event.spatialIoPos) instanceof IGridConnectedBlockEntity port)) return null;
        var node = port.getMainNode().getNode();
        return node == null ? null : node.getOwningPlayerProfileId();
    }
}
