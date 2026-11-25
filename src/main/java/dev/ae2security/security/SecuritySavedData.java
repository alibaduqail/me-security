package dev.ae2security.security;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

/** World-owned records. Tombstones distinguish deliberately removed terminals from missing data. */
public final class SecuritySavedData extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NAME = "ae2security_policies";
    private static final String FILE = "data/" + NAME + ".dat";

    /**
     * Minecraft catches every Exception thrown while reading saved data, logs it and carries on with an
     * empty instance that the next autosave writes over the original file. Unreadable policy data is
     * therefore reported as an Error, which stops the server with the file intact.
     */
    public static final class CorruptDataError extends Error {
        private CorruptDataError(String detail, Throwable cause) {
            super("ME Security could not read its saved policies from " + FILE + " in the world folder: " + detail
                    + ". Restore that file from a backup, or delete it to reset every ME Security Terminal to the"
                    + " player who placed it with no trusted players. The server is stopping so that the file is"
                    + " not overwritten with empty data.", cause);
        }
    }

    public record ChunkKey(String dimension, int x, int z) {}
    private final Map<UUID, SecurityPolicy> policies = new HashMap<>();
    private final Map<UUID, Set<ChunkKey>> footprints = new HashMap<>();
    private final Set<UUID> retired = new HashSet<>();
    private final Map<UUID, String> players = new HashMap<>();
    private final Map<UUID, String> playersView = Collections.unmodifiableMap(players);
    private record CraftedOutput(UUID terminal, UUID player, AEKey key) {}
    private final Map<CraftedOutput, Long> craftedOutputs = new HashMap<>();
    private long directoryRevision;

    public static SecuritySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                factory(() -> server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(NAME + ".dat")), NAME);
    }

    /**
     * The constructor half only runs when nothing was loaded. If the file is nevertheless on disk, the
     * read failed below {@link #load} (a truncated or non-NBT file) and starting empty would lose it.
     */
    static Factory<SecuritySavedData> factory(Supplier<Path> file) {
        return new Factory<>(() -> {
            if (Files.exists(file.get())) throw new CorruptDataError("the file exists but is not readable NBT", null);
            return new SecuritySavedData();
        }, SecuritySavedData::load);
    }

    public SecurityPolicy policy(UUID terminal) { return policies.get(terminal); }
    public boolean retired(UUID terminal) { return retired.contains(terminal); }
    /** A live read-only view, not a copy: do not hold it across a tick. */
    public Map<UUID, String> players() { return playersView; }
    public boolean knows(UUID player) { return players.containsKey(player); }
    public long directoryRevision() { return directoryRevision; }
    public String name(UUID player) { return players.getOrDefault(player, player.toString()); }

    /** Completed final outputs only; authorization is rechecked against the current grid on collection. */
    public long craftedAmount(UUID terminal, UUID player, AEKey key) {
        return craftedOutputs.getOrDefault(new CraftedOutput(terminal, player, key), 0L);
    }

    public void recordCraftedOutput(UUID terminal, UUID player, GenericStack output) {
        var policy = policies.get(terminal);
        if (policy == null || retired.contains(terminal) || output == null || output.amount() <= 0) return;
        // A job that finishes after its requester lost access must not bank an allowance for later.
        if (!hasStanding(policy, player)) return;
        var claim = new CraftedOutput(terminal, player, output.what());
        long previous = craftedOutputs.getOrDefault(claim, 0L);
        craftedOutputs.put(claim, previous + Math.min(output.amount(), Long.MAX_VALUE - previous));
        setDirty();
    }

    public void consumeCraftedOutput(UUID terminal, UUID player, AEKey key, long amount) {
        if (amount <= 0) return;
        var claim = new CraftedOutput(terminal, player, key);
        long previous = craftedOutputs.getOrDefault(claim, 0L);
        if (previous == 0) return;
        if (amount >= previous) craftedOutputs.remove(claim);
        else craftedOutputs.put(claim, previous - amount);
        setDirty();
    }

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
        var previous = policies.put(terminal, policy);
        dropLapsedClaims(terminal, previous, policy);
        setDirty();
    }

    /** Uncollected allowances end with the access that earned them; trusting the player again grants none back. */
    private void dropLapsedClaims(UUID terminal, SecurityPolicy previous, SecurityPolicy current) {
        if (craftedOutputs.isEmpty()) return;
        boolean ownerChanged = !previous.owner().equals(current.owner());
        if (!ownerChanged && current.trusted().keySet().containsAll(previous.trusted().keySet())) return;
        craftedOutputs.keySet().removeIf(claim -> claim.terminal().equals(terminal)
                && (ownerChanged && claim.player().equals(previous.owner()) || !hasStanding(current, claim.player())));
    }

    private static boolean hasStanding(SecurityPolicy policy, UUID player) {
        return policy.isOwner(player) || policy.trusted().containsKey(player);
    }

    public void retire(UUID terminal) {
        policies.remove(terminal);
        footprints.remove(terminal);
        retired.add(terminal);
        craftedOutputs.keySet().removeIf(claim -> claim.terminal().equals(terminal));
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
        // Malformed policy or tombstone data is a world error, never a reason to make storage public or
        // to start over empty. It must leave as an Error: see CorruptDataError.
        try {
            for (var element : strictList(tag, "policies", Tag.TAG_COMPOUND)) {
                var row = (CompoundTag) element;
                if (!row.hasUUID("terminal")) throw new IllegalArgumentException("a policy entry has no terminal id");
                var id = row.getUUID("terminal");
                if (!row.contains("policy", Tag.TAG_COMPOUND)) {
                    throw new IllegalArgumentException("terminal " + id + " has no policy");
                }
                data.policies.put(id, PolicyNbt.read(row.getCompound("policy")));
                var chunks = new HashSet<ChunkKey>();
                for (var c : row.getList("chunks", Tag.TAG_COMPOUND)) {
                    var chunk = (CompoundTag) c;
                    chunks.add(new ChunkKey(chunk.getString("dimension"), chunk.getInt("x"), chunk.getInt("z")));
                }
                data.footprints.put(id, chunks);
            }
            for (var element : strictList(tag, "retired", Tag.TAG_INT_ARRAY)) data.retired.add(NbtUtils.loadUUID(element));
        } catch (RuntimeException e) {
            throw new CorruptDataError(e.getMessage() == null ? e.toString() : e.getMessage(), e);
        }
        // The directory and the allowances grant nothing by themselves, so one bad entry is dropped.
        for (var element : tag.getList("players", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) element;
            var name = row.getString("name");
            if (!row.hasUUID("id") || name.isEmpty()) {
                LOGGER.warn("Skipping an unreadable player directory entry in {}: {}", FILE, row);
                continue;
            }
            data.players.put(row.getUUID("id"), name);
        }
        for (var element : tag.getList("craftedOutputs", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) element;
            try {
                if (!row.hasUUID("terminal") || !row.hasUUID("player")) {
                    throw new IllegalArgumentException("missing terminal or player id");
                }
                data.recordCraftedOutput(row.getUUID("terminal"), row.getUUID("player"),
                        GenericStack.readTag(registries, row.getCompound("output")));
            } catch (RuntimeException e) {
                LOGGER.warn("Skipping an unreadable crafted-output allowance in {}: {}", FILE, e.toString());
            }
        }
        return data;
    }

    /** Unlike getList, a present key of the wrong shape is an error instead of an empty list. */
    private static ListTag strictList(CompoundTag tag, String key, int elementType) {
        if (!tag.contains(key)) return new ListTag();
        if (!(tag.get(key) instanceof ListTag list) || !list.isEmpty() && list.getElementType() != elementType) {
            throw new IllegalArgumentException("'" + key + "' is not a list of the expected kind");
        }
        return list;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("format", 3);
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
        var outputs = new ListTag();
        craftedOutputs.forEach((claim, amount) -> {
            var row = new CompoundTag();
            row.putUUID("terminal", claim.terminal());
            row.putUUID("player", claim.player());
            row.put("output", GenericStack.writeTag(registries, new GenericStack(claim.key(), amount)));
            outputs.add(row);
        });
        tag.put("craftedOutputs", outputs);
        return tag;
    }
}
