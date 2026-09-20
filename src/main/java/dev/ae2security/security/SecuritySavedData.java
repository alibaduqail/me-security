package dev.ae2security.security;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** World-owned records. Tombstones distinguish deliberately removed terminals from missing data. */
public final class SecuritySavedData extends SavedData {
    private static final String NAME = "ae2security_policies";
    public record ChunkKey(String dimension, int x, int z) {}
    private final Map<UUID, SecurityPolicy> policies = new HashMap<>();
    private final Map<UUID, Set<ChunkKey>> footprints = new HashMap<>();
    private final Set<UUID> retired = new HashSet<>();
    private final Map<UUID, String> players = new HashMap<>();
    private long directoryRevision;

    public static SecuritySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(SecuritySavedData::new, SecuritySavedData::load), NAME);
    }

    public SecurityPolicy policy(UUID terminal) { return policies.get(terminal); }
    public boolean retired(UUID terminal) { return retired.contains(terminal); }
    public Map<UUID, String> players() { return Map.copyOf(players); }
    public long directoryRevision() { return directoryRevision; }
    public String name(UUID player) { return players.getOrDefault(player, player.toString()); }

    public void rememberPlayer(UUID id, String name) {
        if (!Objects.equals(players.put(id, name), name)) {
            directoryRevision++;
            setDirty();
        }
    }

    public void create(UUID terminal, UUID owner) {
        if (policies.containsKey(terminal) || retired.contains(terminal)) {
            throw new IllegalStateException("Security terminal identity already exists");
        }
        policies.put(terminal, SecurityPolicy.create(owner));
        setDirty();
    }

    public void update(UUID terminal, SecurityPolicy policy) {
        if (!policies.containsKey(terminal) || retired.contains(terminal)) {
            throw new IllegalStateException("Security terminal no longer exists");
        }
        policies.put(terminal, policy);
        setDirty();
    }

    public void retire(UUID terminal) {
        policies.remove(terminal);
        footprints.remove(terminal);
        retired.add(terminal);
        setDirty();
    }

    public void rememberChunks(UUID terminal, Collection<ChunkKey> chunks) {
        if (footprints.computeIfAbsent(terminal, key -> new HashSet<>()).addAll(chunks)) setDirty();
    }

    /** Only prove a split when all formerly connected chunks are available. Never loads a chunk. */
    public boolean topologyLoaded(UUID terminal, MinecraftServer server) {
        var chunks = footprints.get(terminal);
        if (chunks == null || chunks.isEmpty()) return false;
        for (var chunk : chunks) {
            var dimension = ResourceLocation.tryParse(chunk.dimension());
            if (dimension == null) return false;
            var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
            if (level == null || !level.hasChunk(chunk.x(), chunk.z())
                    || !level.isPositionEntityTicking(new BlockPos(chunk.x() << 4, 0, chunk.z() << 4))) return false;
        }
        return true;
    }

    public static SecuritySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new SecuritySavedData();
        for (var element : tag.getList("policies", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) element;
            var id = row.getUUID("terminal");
            // Malformed policy data is a world error, never a reason to silently make storage public.
            data.policies.put(id, PolicyNbt.read(row.getCompound("policy")));
            var chunks = new HashSet<ChunkKey>();
            for (var c : row.getList("chunks", Tag.TAG_COMPOUND)) {
                var chunk = (CompoundTag) c;
                chunks.add(new ChunkKey(chunk.getString("dimension"), chunk.getInt("x"), chunk.getInt("z")));
            }
            data.footprints.put(id, chunks);
        }
        for (var element : tag.getList("retired", Tag.TAG_INT_ARRAY)) data.retired.add(NbtUtils.loadUUID(element));
        for (var element : tag.getList("players", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) element;
            data.players.put(row.getUUID("id"), row.getString("name"));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("format", 1);
        var entries = new ListTag();
        policies.forEach((id, policy) -> {
            var row = new CompoundTag();
            row.putUUID("terminal", id);
            row.put("policy", PolicyNbt.write(policy));
            var chunks = new ListTag();
            for (var key : footprints.getOrDefault(id, Set.of())) {
                var chunk = new CompoundTag();
                chunk.putString("dimension", key.dimension());
                chunk.putInt("x", key.x());
                chunk.putInt("z", key.z());
                chunks.add(chunk);
            }
            row.put("chunks", chunks);
            entries.add(row);
        });
        tag.put("policies", entries);
        var tombstones = new ListTag();
        retired.stream().sorted().forEach(id -> tombstones.add(NbtUtils.createUUID(id)));
        tag.put("retired", tombstones);
        var known = new ListTag();
        players.forEach((id, name) -> {
            var row = new CompoundTag();
            row.putUUID("id", id);
            row.putString("name", name);
            known.add(row);
        });
        tag.put("players", known);
        return tag;
    }
}
