package dev.ae2security.security;

import java.util.*;
import appeng.api.networking.*;
import appeng.api.networking.storage.IStorageService;
import appeng.me.InWorldGridNode;
import dev.ae2security.mixin.InWorldGridNodeAccessor;
import dev.ae2security.part.SecurityTerminalPart;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;

/** Tracks policy membership through AE2's existing per-node save and grid migration callbacks. */
public final class SecurityGridService implements ISecurityGrid, IGridServiceProvider {
    private static final String TAG = "ae2security:terminals";
    private final IGrid grid;
    private final Map<IGridNode, Set<UUID>> nodes = new IdentityHashMap<>();
    private final Set<UUID> inherited = new HashSet<>();
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
        if (node.getOwner() instanceof SecurityTerminalPart terminal) ids.add(terminal.terminalId());
        nodes.put(node, ids);
        inherited.addAll(ids);
        stableAfter = server.getTickCount() + 5L;
    }

    @Override
    public void removeNode(IGridNode node) {
        // Keep inherited identities until the new topology can be proven; callbacks run mid-migration.
        var removed = nodes.remove(node);
        if (removed != null) inherited.addAll(removed);
        if (server != null) stableAfter = server.getTickCount() + 5L;
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

    private Set<UUID> connectedTerminals() {
        var ids = new HashSet<UUID>();
        for (var node : nodes.keySet()) {
            if (node.getOwner() instanceof SecurityTerminalPart terminal) ids.add(terminal.terminalId());
        }
        return ids;
    }

    @Override
    public void onServerEndTick() {
        if (server == null || nodes.isEmpty()) return;
        var data = SecuritySavedData.get(server);
        var present = connectedTerminals();
        inherited.addAll(present);
        inherited.removeIf(data::retired);

        var chunks = new HashSet<SecuritySavedData.ChunkKey>();
        for (var node : nodes.keySet()) {
            if (node instanceof InWorldGridNode) {
                var pos = ((InWorldGridNodeAccessor) node).ae2security$location();
                chunks.add(new SecuritySavedData.ChunkKey(node.getLevel().dimension().location().toString(),
                        pos.getX() >> 4, pos.getZ() >> 4));
            }
        }
        for (var id : present) data.rememberChunks(id, chunks);

        if (server.getTickCount() >= stableAfter) {
            inherited.removeIf(id -> !present.contains(id) && data.policy(id) != null
                    && data.topologyLoaded(id, server));
        }
        nodes.forEach((node, hints) -> {
            if (!hints.equals(inherited)) {
                hints.clear();
                hints.addAll(inherited);
                ((appeng.me.GridNode) node).callListener(IGridNodeListener::onSaveChanges);
            }
        });
    }

    @Override
    public Set<UUID> terminalIds() {
        var ids = new HashSet<>(inherited);
        ids.addAll(connectedTerminals());
        if (server != null) ids.removeIf(SecuritySavedData.get(server)::retired);
        return Set.copyOf(ids);
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
