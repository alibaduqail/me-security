package dev.ae2security.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.ae2security.client.PlayerListState.Update;
import dev.ae2security.network.PlayerSearchRequest;
import dev.ae2security.network.PlayerSearchResult;
import dev.ae2security.network.PolicySnapshot;
import dev.ae2security.security.Permission;
import dev.ae2security.security.PlayerDirectorySearch;
import dev.ae2security.security.SecurityPolicy;
import org.junit.jupiter.api.Test;

/** Drives the screen's list bookkeeping against the real server-side directory search. */
class PlayerListStateTest {
    private static final int MENU = 7;
    private static final int PLAYERS = 40;

    private final UUID owner = new UUID(9, 9);
    private final Map<UUID, String> directory = new HashMap<>();
    private final List<PlayerSearchRequest> sent = new ArrayList<>();
    private final PlayerListState state = new PlayerListState();
    private SecurityPolicy policy = SecurityPolicy.create(owner);
    private Update lastUpdate;

    PlayerListStateTest() {
        directory.put(owner, "Owner");
        for (int i = 0; i < PLAYERS; i++) directory.put(id(i), name(i));
        state.refresh(true);
        settle();
    }

    private static UUID id(int index) {
        return new UUID(1, index);
    }

    private static String name(int index) {
        return String.format("P%02d", index);
    }

    private PlayerSearchResult serve(PlayerSearchRequest request) {
        return PlayerDirectorySearch.search(MENU, request.sequence(), owner, directory, policy,
                request.query(), request.page());
    }

    /** Ticks until the next request is due and returns it without answering. */
    private PlayerSearchRequest nextRequest() {
        for (int tick = 0; tick < 20; tick++) {
            var request = state.poll(MENU);
            if (request != null) {
                sent.add(request);
                return request;
            }
        }
        return fail("No search request became due");
    }

    private void settle() {
        for (int exchange = 0; state.searching(); exchange++) {
            if (exchange == 10) fail("The list never settled");
            lastUpdate = state.accept(serve(nextRequest()));
        }
    }

    private void turnTo(int page) {
        while (state.page() < page) {
            state.turnPage(1);
            settle();
        }
    }

    /** What the screen does when a snapshot with a new policy or directory revision arrives. */
    private void policyChanged(SecurityPolicy updated) {
        policy = updated;
        state.refresh(false);
        settle();
    }

    private List<UUID> listedIds() {
        return state.players().stream().map(PolicySnapshot.PlayerEntry::id).toList();
    }

    @Test
    void trustingAPlayerOnALaterPageKeepsThemSelectedWithTheirNewState() {
        turnTo(3);
        assertTrue(state.select(2));
        var target = state.selection().id();
        assertEquals(id(20), target);
        sent.clear();

        policyChanged(policy.trust(owner, target, Permission.VIEW.bit()));

        var selection = state.selection();
        assertNotNull(selection, "the player who was just trusted must stay selected");
        assertEquals(target, selection.id());
        assertTrue(selection.trusted(), "permission controls depend on the refreshed trust state");
        assertEquals(Permission.VIEW.bit(), selection.permissions());
        assertEquals(3, state.page(), "the owner is not thrown to another page");
        assertFalse(listedIds().contains(target), "the server now sorts the player onto page 0");
        assertFalse(state.searching());
        assertEquals(List.of("", target.toString()), sent.stream().map(PlayerSearchRequest::query).toList(),
                "one page refresh, then one lookup of the selected player by UUID");

        policyChanged(policy.trust(owner, target, Permission.VIEW.bit() | Permission.CRAFT.bit()));

        assertEquals(Permission.VIEW.bit() | Permission.CRAFT.bit(), state.selection().permissions(),
                "permission toggles on an off-page selection are still reflected");
    }

    @Test
    void removingTrustKeepsThePlayerSelectedAsUntrusted() {
        var target = id(37);
        policy = policy.trust(owner, target, Permission.ALL);
        state.refresh(true);
        settle();
        assertEquals(0, state.page());
        assertTrue(state.select(0));
        assertEquals(target, state.selection().id());
        assertTrue(state.selection().trusted());

        policyChanged(policy.remove(owner, target));

        assertFalse(listedIds().contains(target), "an untrusted P37 sorts onto the last page");
        assertNotNull(state.selection());
        assertEquals(target, state.selection().id());
        assertFalse(state.selection().trusted());
        assertEquals(0, state.selection().permissions());
        assertEquals(Update.SELECTION_STALE, lastUpdate, "a transfer confirmation must not survive removal");
    }

    @Test
    void selectionStillOnThePageIsRefreshedWithoutALookup() {
        assertTrue(state.select(4));
        var target = state.selection().id();
        sent.clear();

        policyChanged(policy.trust(owner, target, Permission.VIEW.bit()));

        assertEquals(1, sent.size());
        assertEquals(target, state.players().getFirst().id());
        assertTrue(state.selection().trusted());
        assertEquals(Update.REFRESHED, lastUpdate);
    }

    @Test
    void pagingAndTypingStillClearTheSelection() {
        assertTrue(state.select(1));
        state.turnPage(1);
        assertNull(state.selection());
        assertTrue(state.players().isEmpty());
        settle();
        assertEquals(1, state.page());
        assertEquals(Update.SELECTION_STALE, lastUpdate);

        assertTrue(state.select(1));
        state.setQuery("p3");
        assertNull(state.selection());
        settle();
        assertEquals(0, state.page());
        assertEquals("p3", sent.getLast().query());
        assertEquals(6, state.players().size());
        assertEquals(1, state.lastPage());
    }

    @Test
    void selectionIsDroppedOnceThePlayerLeavesTheDirectory() {
        turnTo(2);
        assertTrue(state.select(0));
        directory.remove(state.selection().id());

        policyChanged(policy);

        assertNull(state.selection());
        assertEquals(Update.SELECTION_STALE, lastUpdate);
        assertFalse(state.searching());
    }

    @Test
    void choosingAnotherRowDuringTheLookupKeepsTheNewChoice() {
        turnTo(3);
        assertTrue(state.select(2));
        policy = policy.trust(owner, state.selection().id(), Permission.VIEW.bit());
        state.refresh(false);
        state.accept(serve(nextRequest()));
        assertTrue(state.searching(), "the lookup is still outstanding");

        assertTrue(state.select(0));
        var chosen = state.selection();
        state.accept(serve(nextRequest()));

        assertEquals(chosen, state.selection());
        assertFalse(state.searching());
    }

    @Test
    void busyAndOutdatedAnswersLeaveTheListAlone() {
        var rows = state.players();
        state.refresh(false);
        var request = nextRequest();

        assertEquals(Update.IGNORED, state.accept(new PlayerSearchResult(MENU, request.sequence() - 1, 5, 99,
                false, List.of())));
        assertEquals(Update.IGNORED, state.accept(new PlayerSearchResult(MENU, request.sequence(), 0, 0,
                true, List.of())));
        assertTrue(state.searching());
        assertEquals(rows, state.players());
        assertEquals(0, state.page());

        assertEquals(request, nextRequest(), "a busy server is asked again for the same page");
    }

    @Test
    void pageBeyondTheEndIsClampedAndRequestedAgain() {
        turnTo(6);
        assertEquals(4, state.players().size());
        for (int i = 30; i < PLAYERS; i++) directory.remove(id(i));

        policyChanged(policy);

        assertEquals(4, state.page());
        assertEquals(4, state.lastPage());
        assertEquals(6, state.players().size());
        assertFalse(state.searching());
    }

    @Test
    void rowsOutsideThePageCannotBeSelected() {
        turnTo(6);
        assertFalse(state.select(4));
        assertFalse(state.select(-1));
        assertNull(state.selection());
    }
}
