package dev.ae2security.security;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/**
 * The denied attempts on one terminal's network that its owner has not been told about yet, one entry
 * per intruder. It only holds what is waiting for the owner's next login, so it is bounded and emptied
 * on delivery; it is not an access history.
 */
public final class AlarmLog {
    /** Enough to name everyone in an ordinary raid; beyond it the intruder seen longest ago is dropped. */
    public static final int MAX_INTRUDERS = 16;
    private static final int MAX_NAME = 64;

    /** @param name the name the intruder had when last denied, since a fake player is in no directory */
    public record Entry(UUID player, String name, int count, long lastMillis) {}

    private final Map<UUID, Entry> entries = new HashMap<>();

    public boolean isEmpty() { return entries.isEmpty(); }

    public void record(UUID player, String name, long nowMillis) {
        var previous = entries.get(player);
        if (previous == null && entries.size() >= MAX_INTRUDERS) {
            entries.values().stream().min(Comparator.comparingLong(Entry::lastMillis))
                    .ifPresent(oldest -> entries.remove(oldest.player()));
        }
        int count = previous == null ? 1 : previous.count() == Integer.MAX_VALUE ? previous.count() : previous.count() + 1;
        entries.put(player, new Entry(player, clip(name), count,
                previous == null ? nowMillis : Math.max(previous.lastMillis(), nowMillis)));
    }

    /** Most recent first; intruders denied at the same moment are ordered by name so the result is stable. */
    public List<Entry> entries() {
        var sorted = new ArrayList<>(entries.values());
        sorted.sort(Comparator.comparingLong(Entry::lastMillis).reversed().thenComparing(Entry::name)
                .thenComparing(Entry::player));
        return sorted;
    }

    private static String clip(String name) {
        return name.length() <= MAX_NAME ? name : name.substring(0, MAX_NAME);
    }

    public ListTag write() {
        var list = new ListTag();
        for (var entry : entries()) {
            var row = new CompoundTag();
            row.putUUID("player", entry.player());
            row.putString("name", entry.name());
            row.putInt("count", entry.count());
            row.putLong("last", entry.lastMillis());
            list.add(row);
        }
        return list;
    }

    /** Pending alerts grant nothing, so an unreadable entry is skipped instead of failing the world load. */
    public static AlarmLog read(ListTag list) {
        var log = new AlarmLog();
        for (var element : list) {
            if (!(element instanceof CompoundTag row) || !row.hasUUID("player") || row.getInt("count") <= 0) continue;
            if (log.entries.size() >= MAX_INTRUDERS) break;
            var player = row.getUUID("player");
            log.entries.put(player, new Entry(player, clip(row.getString("name")), row.getInt("count"), row.getLong("last")));
        }
        return log;
    }
}
