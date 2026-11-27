package dev.ae2security.security;

import java.util.*;
import appeng.api.AECapabilities;
import appeng.api.networking.*;
import appeng.api.networking.storage.IStorageService;
import appeng.me.InWorldGridNode;
import dev.ae2security.mixin.InWorldGridNodeAccessor;
import dev.ae2security.part.SecurityTerminalPart;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

/** Tracks policy membership through AE2's existing per-node save and grid migration callbacks. */
public final class SecurityGridService implements ISecurityGrid, IGridServiceProvider {
    private static final String TAG = "ae2security:terminals";
    private final IGrid grid;
    private final Map<IGridNode, Set<UUID>> nodes = new IdentityHashMap<>();
    /**
     * The nodes in {@link #nodes} that belong to a Security Terminal, so that no caller has to walk the
     * grid. Only the nodes are tracked, never their identities: AE2 reloads a live part in place
     * (CableBusContainer.loadPart), and SecurityTerminalPart.readFromNBT replaces its identity after
     * AEBasePart.readFromNBT may already have re-added the node, so an identity captured in addNode can
     * be stale. Identities are read from the parts whenever they are needed.
     */
    private final List<IGridNode> terminalNodes = new ArrayList<>();
    private final Set<UUID> inherited = new HashSet<>();
    /**
     * Identities that a copied terminal on this grid carried before it received its own. Nodes copied with
     * it carry them as saved hints and may join over the next ticks; those hints are dropped until the
     * grid has been stable for a few ticks.
     */
    private final Set<UUID> disowned = new HashSet<>();
    /** False only while every node's hints are known to equal {@link #inherited}. */
    private boolean hintsStale;
    /** Chunks of all in-world nodes, or null when an in-world node joined or left since it was built. */
    private Set<SecuritySavedData.ChunkKey> chunks;
    private MinecraftServer server;
    private long stableAfter;
    /** Ticks a remembered chunk outside the footprint must stay ready before it can be forgotten. */
    static final int SETTLE_TICKS = 40;
    /** How often a settled chunk that still holds AE2 hardware is scanned again. */
    private static final int OCCUPIED_RECHECK_TICKS = 20;
    /** The footprint last reconciled for each present terminal, and what was known then. */
    private record Reconciled(Set<SecuritySavedData.ChunkKey> footprint, long revision, boolean outside) {}
    private final Map<UUID, Reconciled> reconciled = new HashMap<>();
    /** Transient: the tick since which each remembered chunk outside the footprint has been ready. */
    private final Map<SecuritySavedData.ChunkKey, Long> readySince = new HashMap<>();
    private long lastSampled = Long.MIN_VALUE;

    public SecurityGridService(IGrid grid, IStorageService storage) {
        this.grid = grid;
        ((GridBoundStorage) storage.getInventory()).ae2security$bind(grid);
    }

    @Override
    public void addNode(IGridNode node, CompoundTag savedData) {
        server = node.getLevel().getServer();
        var ids = new HashSet<UUID>();
        if (savedData != null) {
            for (var entry : savedData.getList(TAG, Tag.TAG_INT_ARRAY)) ids.add(NbtUtils.loadUUID(entry));
            if (!disowned.isEmpty()) ids.removeAll(disowned);
        }
        boolean terminalNode = false;
        if (node.getOwner() instanceof SecurityTerminalPart terminal) {
            ids.add(terminal.terminalId());
            terminalNode = true;
        }
        if (nodes.put(node, ids) == null && terminalNode) terminalNodes.add(node);
        // ids is a subset of inherited from here on, so the two are equal exactly when their sizes are.
        if (inherited.addAll(ids) || ids.size() != inherited.size()) hintsStale = true;
        if (node instanceof InWorldGridNode) chunks = null;
        stableAfter = server.getTickCount() + 5L;
    }

    @Override
    public void removeNode(IGridNode node) {
        // Keep inherited identities until the new topology can be proven; callbacks run mid-migration.
        var removed = nodes.remove(node);
        if (removed != null) {
            if (inherited.addAll(removed)) hintsStale = true;
            if (node.getOwner() instanceof SecurityTerminalPart) forgetTerminalNode(node);
            if (node instanceof InWorldGridNode) chunks = null;
        }
        if (server != null) stableAfter = server.getTickCount() + 5L;
    }

    private void forgetTerminalNode(IGridNode node) {
        for (int i = terminalNodes.size() - 1; i >= 0; i--) {
            if (terminalNodes.get(i) == node) {
                terminalNodes.remove(i);
                return;
            }
        }
    }

    /**
     * A terminal on this grid was found to be a copy and now carries its own identity. The grid was only
     * associated with the copied identity through data copied along with the terminal, so it is removed
     * from every hint unless the copied identity's own terminal is on this grid as well.
     */
    public void disownCopiedIdentity(UUID copied) {
        for (int i = 0; i < terminalNodes.size(); i++) {
            if (terminalId(terminalNodes.get(i)).equals(copied)) return;
        }
        disowned.add(copied);
        if (server != null) stableAfter = Math.max(stableAfter, server.getTickCount() + 5L);
        inherited.remove(copied);
        nodes.forEach((node, hints) -> {
            if (hints.remove(copied)) ((appeng.me.GridNode) node).callListener(IGridNodeListener::onSaveChanges);
        });
    }

    @Override
    public void saveNodeData(IGridNode node, CompoundTag tag) {
        var ids = new HashSet<>(inherited);
        ids.addAll(connectedTerminals());
        if (server != null) ids.removeIf(SecuritySavedData.get(server)::retired);
        var list = new ListTag();
        ids.stream().sorted().forEach(id -> list.add(NbtUtils.createUUID(id)));
        tag.put(TAG, list);
    }

    private static UUID terminalId(IGridNode terminalNode) {
        return ((SecurityTerminalPart) terminalNode.getOwner()).terminalId();
    }

    /** The loaded terminal on this grid that carries the identity, or null when it is only known from hints. */
    public SecurityTerminalPart terminal(UUID id) {
        for (int i = 0; i < terminalNodes.size(); i++) {
            var terminal = (SecurityTerminalPart) terminalNodes.get(i).getOwner();
            if (terminal.terminalId().equals(id)) return terminal;
        }
        return null;
    }

    private Set<UUID> connectedTerminals() {
        var ids = new HashSet<UUID>();
        for (int i = 0; i < terminalNodes.size(); i++) ids.add(terminalId(terminalNodes.get(i)));
        return ids;
    }

    /** The chunk of every in-world node. A built set is never modified, only replaced. */
    private Set<SecuritySavedData.ChunkKey> chunkFootprint() {
        if (chunks != null) return chunks;
        var perLevel = new IdentityHashMap<Level, LongOpenHashSet>(2);
        Level lastLevel = null;
        LongOpenHashSet packed = null;
        for (var node : nodes.keySet()) {
            if (node instanceof InWorldGridNode) {
                Level level = node.getLevel();
                if (level != lastLevel) {
                    lastLevel = level;
                    packed = perLevel.computeIfAbsent(level, key -> new LongOpenHashSet());
                }
                var pos = ((InWorldGridNodeAccessor) node).ae2security$location();
                packed.add(((long) (pos.getX() >> 4) << 32) | ((pos.getZ() >> 4) & 0xFFFFFFFFL));
            }
        }
        var built = new HashSet<SecuritySavedData.ChunkKey>();
        for (var entry : perLevel.entrySet()) {
            var dimension = entry.getKey().dimension().location().toString();
            for (var chunk = entry.getValue().iterator(); chunk.hasNext();) {
                long key = chunk.nextLong();
                built.add(new SecuritySavedData.ChunkKey(dimension, (int) (key >> 32), (int) key));
            }
        }
        chunks = Collections.unmodifiableSet(built);
        return chunks;
    }

    /**
     * Keeps a present terminal's remembered chunks equal to its current footprint plus the chunks that
     * cannot yet be shown to hold nothing connected to it. Nothing is written while that stays true.
     */
    private void reconcile(SecuritySavedData data, UUID id, Set<SecuritySavedData.ChunkKey> footprint, boolean stable) {
        var last = reconciled.get(id);
        if (last != null && last.footprint() == footprint && last.revision() == data.footprintRevision()
                && !(stable && last.outside())) return;
        if (stable) {
            var stored = data.footprint(id);
            var forget = forgettable(stored, footprint);
            data.reconcileChunks(id, footprint, forget::contains);
        } else {
            data.reconcileChunks(id, footprint, chunk -> false);
        }
        reconciled.put(id, new Reconciled(footprint, data.footprintRevision(),
                data.footprint(id).size() > footprint.size()));
    }

    /**
     * The remembered chunks outside the current footprint of this stable grid that may be forgotten.
     *
     * <p>Release relies on every remembered chunk being ready, so forgetting one must never hide a chunk
     * through which a detached piece may still be connected. Being ready and outside the footprint is not
     * enough on its own: with A(terminal)-B-C-D, B unloaded and C, D loaded, C and D look empty of this grid
     * although they still connect D to it through B. Forgetting them would release D later, once C unloads
     * and B loads. A chunk is therefore forgotten only when one of these holds:
     * <ul>
     * <li>Its dimension is gone. No node exists there, so no connection can run through it.
     * <li>It has been ready continuously for {@link #SETTLE_TICKS} and holds no AE2 grid-node host at all.
     * Nothing can connect through it, so it is the same as a chunk the network never reached.
     * <li>It has been ready that long, and at the same moment every remembered chunk is ready, the others
     * outside the footprint for that long too. All chunks the network ever reached are then live, which is
     * exactly when release already accepts the grid as the whole topology.
     * </ul>
     * The settle time covers block entities in a newly ready chunk whose nodes have not joined yet. Readiness
     * must be seen on consecutive ticks; a tick without a sample restarts every chunk's settle time.
     */
    private Set<SecuritySavedData.ChunkKey> forgettable(Set<SecuritySavedData.ChunkKey> stored,
            Set<SecuritySavedData.ChunkKey> current) {
        long now = server.getTickCount();
        if (lastSampled != now && lastSampled != now - 1) readySince.clear();
        lastSampled = now;
        var forget = new HashSet<SecuritySavedData.ChunkKey>();
        List<SecuritySavedData.ChunkKey> occupied = null;
        boolean allSettled = true;
        for (var chunk : stored) {
            if (current.contains(chunk)) continue;
            if (SecuritySavedData.dimensionGone(server, chunk)) {
                forget.add(chunk);
                continue;
            }
            if (!SecuritySavedData.chunkReady(server, chunk)) {
                readySince.remove(chunk);
                allSettled = false;
                continue;
            }
            long age = now - readySince.computeIfAbsent(chunk, key -> now);
            if (age < SETTLE_TICKS) {
                allSettled = false;
            } else if ((age - SETTLE_TICKS) % OCCUPIED_RECHECK_TICKS == 0 && holdsNoGridHardware(chunk)) {
                forget.add(chunk);
            } else {
                if (occupied == null) occupied = new ArrayList<>();
                occupied.add(chunk);
            }
        }
        if (allSettled && occupied != null && allReady(current)) forget.addAll(occupied);
        readySince.keySet().removeIf(chunk -> current.contains(chunk) || !stored.contains(chunk));
        return forget;
    }

    private boolean allReady(Set<SecuritySavedData.ChunkKey> chunks) {
        for (var chunk : chunks) {
            if (!SecuritySavedData.chunkReady(server, chunk)) return false;
        }
        return true;
    }

    /** True only for a loaded chunk without any block entity that can host an AE2 in-world grid node. */
    private boolean holdsNoGridHardware(SecuritySavedData.ChunkKey key) {
        var level = SecuritySavedData.level(server, key);
        var chunk = level == null ? null : level.getChunkSource().getChunkNow(key.x(), key.z());
        if (chunk == null) return false;
        for (var entry : chunk.getBlockEntities().entrySet()) {
            var blockEntity = entry.getValue();
            if (blockEntity instanceof IInWorldGridNodeHost || level.getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST,
                    entry.getKey(), blockEntity.getBlockState(), blockEntity, null) != null) return false;
        }
        return true;
    }

    @Override
    public void onServerEndTick() {
        if (server == null || nodes.isEmpty()) return;
        if (!disowned.isEmpty() && server.getTickCount() >= stableAfter) disowned.clear();
        // Nothing below can have an effect for a grid without identities whose hints are all in sync.
        if (terminalNodes.isEmpty() && inherited.isEmpty() && !hintsStale) return;
        var data = SecuritySavedData.get(server);
        var present = connectedTerminals();
        if (inherited.addAll(present)) hintsStale = true;
        if (inherited.removeIf(data::retired)) hintsStale = true;

        if (!present.isEmpty()) {
            var footprint = chunkFootprint();
            boolean stable = server.getTickCount() >= stableAfter;
            if (reconciled.size() > present.size()) reconciled.keySet().retainAll(present);
            for (var id : present) reconcile(data, id, footprint, stable);
        }

        int beforeRelease = inherited.size();
        if (server.getTickCount() >= stableAfter) {
            inherited.removeIf(id -> !present.contains(id) && data.policy(id) != null
                    && data.topologyLoaded(id, server));
        }
        if (inherited.size() != beforeRelease) hintsStale = true;

        if (!hintsStale) return;
        nodes.forEach((node, hints) -> {
            if (!hints.equals(inherited)) {
                hints.clear();
                hints.addAll(inherited);
                ((appeng.me.GridNode) node).callListener(IGridNodeListener::onSaveChanges);
            }
        });
        hintsStale = false;
    }

    @Override
    public Set<UUID> terminalIds() {
        if (inherited.isEmpty() && terminalNodes.isEmpty()) return Set.of();
        var data = server == null ? null : SecuritySavedData.get(server);
        UUID single = null;
        Set<UUID> several = null;
        var stored = inherited.iterator();
        for (int i = 0; stored.hasNext() || i < terminalNodes.size();) {
            var id = stored.hasNext() ? stored.next() : terminalId(terminalNodes.get(i++));
            if (data != null && data.retired(id)) continue;
            if (several != null) {
                several.add(id);
            } else if (single == null) {
                single = id;
            } else if (!single.equals(id)) {
                several = new HashSet<>();
                several.add(single);
                several.add(id);
            }
        }
        if (several != null) return Set.copyOf(several);
        return single == null ? Set.of() : Set.of(single);
    }

    @Override
    public AccessDecision check(UUID player, Permission permission, boolean ownerOnly) {
        var ids = terminalIds();
        if (ids.isEmpty()) return AccessDecision.ALLOW;
        if (server == null) return AccessDecision.UNRESOLVED;
        var data = SecuritySavedData.get(server);
        var policies = new ArrayList<SecurityPolicy>();
        for (var id : ids) {
            var policy = data.policy(id);
            if (policy == null) return AccessDecision.UNRESOLVED;
            policies.add(policy);
        }
        return AccessDecision.evaluate(policies, false, player, permission, ownerOnly);
    }
}
