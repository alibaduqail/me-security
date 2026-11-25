package dev.ae2security.security;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.ae2security.network.PlayerSearchResult;
import dev.ae2security.network.PolicySnapshot;
import org.junit.jupiter.api.Test;

class PlayerDirectorySearchTest {
    @Test
    void searchesBeyondTheOldSnapshotLimitByNameAndUuid() {
        var owner = UUID.randomUUID();
        var target = UUID.randomUUID();
        var policy = SecurityPolicy.create(owner);
        var players = new HashMap<UUID, String>();
        players.put(owner, "Owner");
        for (int i = 0; i < 9_000; i++) players.put(UUID.randomUUID(), "Player" + i);
        players.put(target, "NeedleAtTheEnd");

        var byName = PlayerDirectorySearch.search(1, 7, owner, players, policy, "needle", 0);
        assertEquals(1, byName.total());
        assertEquals(target, byName.players().getFirst().id());
        var byUuid = PlayerDirectorySearch.search(1, 8, owner, players, policy,
                target.toString().substring(0, 12), 0);
        assertEquals(target, byUuid.players().getFirst().id());
        assertEquals(6, PlayerDirectorySearch.search(1, 9, owner, players, policy,
                "player", 0).players().size());
        assertEquals(9_000, PlayerDirectorySearch.search(1, 10, owner, players, policy,
                "player", 0).total());
    }

    @Test
    void trustedEntriesSortFirstAndPagesAreStable() {
        var owner = new UUID(9, 9);
        var zed = new UUID(1, 50);
        var amy = new UUID(1, 51);
        var names = new LinkedHashMap<UUID, String>();
        names.put(owner, "Owner");
        names.put(zed, "Zed");
        names.put(amy, "amy");
        // Identical names straddle every page boundary, so only the UUID tie-break keeps pages apart.
        for (int i = 0; i < 14; i++) names.put(new UUID(1, i), i % 2 == 0 ? "Same" : "same");
        var policy = SecurityPolicy.create(owner)
                .trust(owner, zed, Permission.VIEW.bit())
                .trust(owner, amy, Permission.ALL);

        var expected = new ArrayList<UUID>(List.of(amy, zed));
        for (int i = 0; i < 14; i++) expected.add(new UUID(1, i));

        // The same directory in two different iteration orders must page identically.
        var reversed = new LinkedHashMap<UUID, String>();
        new ArrayList<>(names.keySet()).reversed().forEach(id -> reversed.put(id, names.get(id)));
        var hashed = new HashMap<>(names);

        var seen = new ArrayList<UUID>();
        for (int page = 0; page < 3; page++) {
            var result = PlayerDirectorySearch.search(1, page, owner, names, policy, "", page);
            assertEquals(16, result.total());
            assertEquals(page, result.page());
            assertEquals(result.players(),
                    PlayerDirectorySearch.search(1, page, owner, reversed, policy, "", page).players());
            assertEquals(result.players(),
                    PlayerDirectorySearch.search(1, page, owner, hashed, policy, "", page).players());
            result.players().forEach(entry -> seen.add(entry.id()));
        }

        assertEquals(expected, seen, "pages must partition the sorted directory without overlap or gaps");
        assertEquals(16, new HashSet<>(seen).size());
        assertFalse(seen.contains(owner));
        var first = PlayerDirectorySearch.search(1, 1, owner, names, policy, "", 0).players();
        assertTrue(first.get(0).trusted() && first.get(1).trusted());
        assertEquals(Permission.ALL, first.get(0).permissions());
        assertFalse(first.get(2).trusted());
        assertTrue(PlayerDirectorySearch.search(1, 1, owner, names, policy, "", 3).players().isEmpty());
    }

    @Test
    void largeDirectoryReturnsOnlyOneBoundedPage() {
        var owner = UUID.randomUUID();
        var players = new HashMap<UUID, String>();
        players.put(owner, "Owner");
        for (int i = 0; i < 50_000; i++) players.put(UUID.randomUUID(), "Player" + i);
        var policy = SecurityPolicy.create(owner);
        var sorted = players.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(owner))
                .sorted(Map.Entry.<UUID, String>comparingByValue(String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .toList();
        int size = PlayerSearchResult.PAGE_SIZE;

        long start = System.nanoTime();
        var page = assertTimeout(Duration.ofSeconds(5),
                () -> PlayerDirectorySearch.search(1, 1, owner, players, policy, "player", 0));
        long milliseconds = (System.nanoTime() - start) / 1_000_000;
        System.out.println("DIRECTORY_STRESS: 50000_entries_page_ms=" + milliseconds);

        assertEquals(50_000, page.total());
        assertEquals(sorted.subList(0, size), ids(page), "the page is exactly the first six of the full order");

        // Deep, final and out-of-range pages are bounded the same way.
        int deep = 4_000;
        assertEquals(sorted.subList(deep * size, (deep + 1) * size),
                ids(PlayerDirectorySearch.search(1, 2, owner, players, policy, "", deep)));
        int last = (50_000 - 1) / size;
        var tail = PlayerDirectorySearch.search(1, 3, owner, players, policy, "", last);
        assertEquals(sorted.subList(last * size, 50_000), ids(tail));
        assertEquals(50_000 - last * size, tail.players().size());
        var beyond = PlayerDirectorySearch.search(1, 4, owner, players, policy, "", last + 1);
        assertTrue(beyond.players().isEmpty());
        assertEquals(50_000, beyond.total());
    }

    private static List<UUID> ids(PlayerSearchResult result) {
        return result.players().stream().map(PolicySnapshot.PlayerEntry::id).toList();
    }
}
