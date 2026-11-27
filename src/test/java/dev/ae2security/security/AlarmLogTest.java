package dev.ae2security.security;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

class AlarmLogTest {
    @Test void repeatedAttemptsByOneIntruderBecomeOneEntryWithTheLatestTimeAndName() {
        var log = new AlarmLog();
        var intruder = UUID.randomUUID();
        log.record(intruder, "OldName", 5_000L);
        log.record(intruder, "NewName", 9_000L);
        log.record(intruder, "NewName", 7_000L);
        assertEquals(List.of(new AlarmLog.Entry(intruder, "NewName", 3, 9_000L)), log.entries());
    }

    @Test void entriesAreListedMostRecentFirst() {
        var log = new AlarmLog();
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        log.record(first, "First", 1_000L);
        log.record(second, "Second", 2_000L);
        assertEquals(List.of("Second", "First"), log.entries().stream().map(AlarmLog.Entry::name).toList());
        log.record(first, "First", 3_000L);
        assertEquals(List.of("First", "Second"), log.entries().stream().map(AlarmLog.Entry::name).toList());
    }

    @Test void aFullLogDropsTheIntruderSeenLongestAgo() {
        var log = new AlarmLog();
        var oldest = UUID.randomUUID();
        log.record(oldest, "Oldest", 1L);
        for (int i = 1; i < AlarmLog.MAX_INTRUDERS; i++) log.record(UUID.randomUUID(), "Intruder" + i, 100L + i);
        assertEquals(AlarmLog.MAX_INTRUDERS, log.entries().size());

        log.record(oldest, "Oldest", 50L);
        assertEquals(AlarmLog.MAX_INTRUDERS, log.entries().size(), "a known intruder never evicts anyone");
        assertTrue(log.entries().stream().anyMatch(entry -> entry.player().equals(oldest)));

        log.record(UUID.randomUUID(), "Newcomer", 500L);
        assertEquals(AlarmLog.MAX_INTRUDERS, log.entries().size());
        assertTrue(log.entries().stream().noneMatch(entry -> entry.player().equals(oldest)));
        assertEquals("Newcomer", log.entries().get(0).name());
    }

    @Test void savingKeepsEveryEntryAndLongNamesAreClipped() {
        var log = new AlarmLog();
        log.record(UUID.randomUUID(), "Steve", 1_000L);
        var repeated = UUID.randomUUID();
        log.record(repeated, "x".repeat(200), 2_000L);
        log.record(repeated, "x".repeat(200), 3_000L);
        var restored = AlarmLog.read(log.write());
        assertEquals(log.entries(), restored.entries());
        assertEquals(64, restored.entries().get(0).name().length());
        assertEquals(log.write(), restored.write());
    }

    @Test void unreadableRowsAreSkippedAndTheListIsBounded() {
        var good = UUID.randomUUID();
        var list = new ListTag();
        list.add(row(null, "NoPlayer", 1));
        list.add(row(UUID.randomUUID(), "NoCount", 0));
        list.add(row(good, "Good", 2));
        for (int i = 0; i < AlarmLog.MAX_INTRUDERS * 2; i++) list.add(row(UUID.randomUUID(), "Extra" + i, 1));
        var log = AlarmLog.read(list);
        assertEquals(AlarmLog.MAX_INTRUDERS, log.entries().size());
        assertTrue(log.entries().stream().anyMatch(entry -> entry.player().equals(good) && entry.count() == 2));
        assertTrue(AlarmLog.read(new ListTag()).isEmpty());
    }

    private static CompoundTag row(UUID player, String name, int count) {
        var row = new CompoundTag();
        if (player != null) row.putUUID("player", player);
        row.putString("name", name);
        row.putInt("count", count);
        row.putLong("last", 1L);
        return row;
    }
}
