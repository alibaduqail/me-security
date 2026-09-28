package dev.ae2security.security;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.UUID;
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
        var owner = UUID.randomUUID();
        var trusted = UUID.randomUUID();
        var players = new HashMap<UUID, String>();
        players.put(owner, "Owner");
        players.put(trusted, "Zed");
        for (int i = 0; i < 8; i++) players.put(UUID.randomUUID(), "A" + i);
        var policy = SecurityPolicy.create(owner).trust(owner, trusted, Permission.VIEW.bit());
        var first = PlayerDirectorySearch.search(1, 1, owner, players, policy, "", 0);
        var second = PlayerDirectorySearch.search(1, 2, owner, players, policy, "", 1);
        assertEquals(9, first.total());
        assertEquals(trusted, first.players().getFirst().id());
        assertEquals(3, second.players().size());
        assertTrue(first.players().stream().noneMatch(entry -> entry.id().equals(owner)));
    }

    @Test
    void largeDirectoryReturnsOnlyOneBoundedPage() {
        var owner = UUID.randomUUID();
        var players = new HashMap<UUID, String>();
        players.put(owner, "Owner");
        for (int i = 0; i < 50_000; i++) players.put(UUID.randomUUID(), "Player" + i);
        long start = System.nanoTime();
        var page = PlayerDirectorySearch.search(1, 1, owner, players,
                SecurityPolicy.create(owner), "player", 0);
        long milliseconds = (System.nanoTime() - start) / 1_000_000;
        assertEquals(50_000, page.total());
        assertEquals(6, page.players().size());
        System.out.println("DIRECTORY_STRESS: 50000_entries_page_ms=" + milliseconds);
    }
}
