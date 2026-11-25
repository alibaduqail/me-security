package dev.ae2security.client;

import java.util.List;
import java.util.UUID;

import dev.ae2security.network.PlayerSearchRequest;
import dev.ae2security.network.PlayerSearchResult;
import dev.ae2security.network.PolicySnapshot;

/**
 * Search, paging and selection bookkeeping for the player list, kept free of widgets so it
 * can be unit tested.
 * <p>
 * The server sorts trusted players first, so trusting or removing the selected player moves
 * it to another page. A refresh that no longer lists the selection therefore keeps it and
 * asks the server for that one player by UUID, instead of silently deselecting it. Only the
 * owner paging or typing a new query, or the player leaving the directory, drops a selection.
 */
final class PlayerListState {
    enum Update {
        /** Stale, duplicate or busy answer; nothing visible changed. */
        IGNORED,
        /** Rows or selection data changed. */
        REFRESHED,
        /** As {@link #REFRESHED}, and a pending ownership-transfer confirmation no longer applies. */
        SELECTION_STALE
    }

    private static final int PAGE_SIZE = PlayerSearchResult.PAGE_SIZE;
    private static final int TYPING_DELAY = 4;
    private static final int BUSY_RETRY_DELAY = 3;
    /** Lets the follow-up lookup clear the server's 100 ms search throttle on the first try. */
    private static final int LOOKUP_DELAY = 2;

    private List<PolicySnapshot.PlayerEntry> players = List.of();
    private PolicySnapshot.PlayerEntry selection;
    private PlayerSearchResult processed;
    private UUID lookup;
    private String query = "";
    private int page;
    private int total;
    private int sequence;
    private int delay = -1;
    private boolean searching;

    List<PolicySnapshot.PlayerEntry> players() {
        return players;
    }

    /** The selected player with the newest data the server has sent, listed on this page or not. */
    PolicySnapshot.PlayerEntry selection() {
        return selection;
    }

    String query() {
        return query;
    }

    int page() {
        return page;
    }

    int lastPage() {
        return Math.max(0, (total - 1) / PAGE_SIZE);
    }

    /** True from the moment a request is scheduled until its answer, and any lookup, is applied. */
    boolean searching() {
        return searching;
    }

    void setQuery(String value) {
        query = value;
        page = 0;
        selection = null;
        schedule(TYPING_DELAY, true);
    }

    void turnPage(int delta) {
        page += delta;
        selection = null;
        schedule(0, true);
    }

    boolean select(int row) {
        if (row < 0 || row >= players.size()) {
            return false;
        }
        selection = players.get(row);
        return true;
    }

    /** Re-requests the current page, keeping the selection. */
    void refresh(boolean clearRows) {
        schedule(0, clearRows);
    }

    private void schedule(int ticks, boolean clearRows) {
        sequence++;
        delay = ticks;
        searching = true;
        lookup = null;
        if (clearRows) {
            players = List.of();
            total = 0;
        }
    }

    /** Call once per tick while the policy is known; returns the request that is now due, if any. */
    PlayerSearchRequest poll(int menuId) {
        if (delay < 0 || --delay >= 0) {
            return null;
        }
        return lookup != null
                ? new PlayerSearchRequest(menuId, sequence, lookup.toString(), 0)
                : new PlayerSearchRequest(menuId, sequence, query, page);
    }

    Update accept(PlayerSearchResult result) {
        if (result == null || result == processed) {
            return Update.IGNORED;
        }
        processed = result;
        if (result.sequence() != sequence) {
            return Update.IGNORED;
        }
        if (result.busy()) {
            delay = BUSY_RETRY_DELAY;
            return Update.IGNORED;
        }

        var before = selection;
        if (lookup != null) {
            var target = lookup;
            lookup = null;
            searching = false;
            if (before != null && before.id().equals(target)) {
                selection = find(result.players(), target);
            }
        } else {
            page = result.page();
            total = result.total();
            players = result.players();
            var listed = before == null ? null : find(players, before.id());
            if (page > lastPage()) {
                page = lastPage();
                schedule(0, true);
            } else if (before != null && listed == null) {
                lookup = before.id();
                sequence++;
                delay = LOOKUP_DELAY;
            } else {
                selection = listed;
                searching = false;
            }
        }
        return selection == null
                || before != null && (!selection.trusted() || !before.name().equals(selection.name()))
                        ? Update.SELECTION_STALE : Update.REFRESHED;
    }

    private static PolicySnapshot.PlayerEntry find(List<PolicySnapshot.PlayerEntry> entries, UUID id) {
        for (var entry : entries) {
            if (entry.id().equals(id)) {
                return entry;
            }
        }
        return null;
    }
}
