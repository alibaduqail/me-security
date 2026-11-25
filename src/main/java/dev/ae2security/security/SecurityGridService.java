package dev.ae2security.security;

import java.util.*;
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
    /** False only while every node's hints are known to equal {@link #inherited}. */
    private boolean hintsStale;
    /** Chunks of all in-world nodes, or null when an in-world node joined or left since it was built. */
    private Set<SecuritySavedData.ChunkKey> chunks;
    private MinecraftServer server;
    private long stableAfter;

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

    @Override
    public void onServerEndTick() {
        if (server == null || nodes.isEmpty()) return;
        // Nothing below can have an effect for a grid without identities whose hints are all in sync.
        if (terminalNodes.isEmpty() && inherited.isEmpty() && !hintsStale) return;
        var data = SecuritySavedData.get(server);
        var present = connectedTerminals();
        if (inherited.addAll(present)) hintsStale = true;
        if (inherited.removeIf(data::retired)) hintsStale = true;

        if (!present.isEmpty()) {
            var footprint = chunkFootprint();
            for (var id : present) data.rememberChunks(id, footprint);
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
