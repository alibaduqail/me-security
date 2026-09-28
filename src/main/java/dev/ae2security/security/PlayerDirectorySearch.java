package dev.ae2security.security;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;

import dev.ae2security.network.PlayerSearchResult;
import dev.ae2security.network.PolicySnapshot;

/** Stable, complete directory filtering with a fixed-size response. */
public final class PlayerDirectorySearch {
    private PlayerDirectorySearch() {}

    public static PlayerSearchResult search(int menuId, int sequence, UUID owner,
            Map<UUID, String> known, SecurityPolicy policy, String query, int page) {
        if (page < 0 || query.length() > 64) throw new IllegalArgumentException("Invalid directory query");
        var filter = query.toLowerCase(Locale.ROOT);
        var order = Comparator.<Map.Entry<UUID, String>, Boolean>comparing(
                        entry -> !policy.trusted().containsKey(entry.getKey()))
                .thenComparing(Map.Entry::getValue, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Map.Entry::getKey);
        long first = (long) page * PlayerSearchResult.PAGE_SIZE;
        long possible = (long) known.size() + policy.trusted().size();
        int capacity = first >= possible ? 0 : (int) Math.min(Integer.MAX_VALUE,
                Math.min(possible, first + PlayerSearchResult.PAGE_SIZE));
        var top = new PriorityQueue<Map.Entry<UUID, String>>(Math.max(1, capacity), order.reversed());
        int total = 0;
        for (var entry : known.entrySet()) {
            if (!entry.getKey().equals(owner) && matches(entry, filter)) {
                total++;
                keep(top, entry, capacity, order);
            }
        }
        for (var id : policy.trusted().keySet()) {
            if (id.equals(owner) || known.containsKey(id)) continue;
            var entry = Map.entry(id, id.toString());
            if (matches(entry, filter)) {
                total++;
                keep(top, entry, capacity, order);
            }
        }
        var sorted = new ArrayList<>(top);
        sorted.sort(order);
        int from = first > sorted.size() ? sorted.size() : (int) first;
        int to = Math.min(sorted.size(), from + PlayerSearchResult.PAGE_SIZE);
        var entries = sorted.subList(from, to).stream()
                .map(entry -> new PolicySnapshot.PlayerEntry(entry.getKey(), entry.getValue(),
                        policy.trusted().containsKey(entry.getKey()), policy.permissions(entry.getKey())))
                .toList();
        return new PlayerSearchResult(menuId, sequence, page, total, false, entries);
    }

    private static boolean matches(Map.Entry<UUID, String> entry, String filter) {
        return entry.getValue().toLowerCase(Locale.ROOT).contains(filter)
                || entry.getKey().toString().contains(filter);
    }

    private static void keep(PriorityQueue<Map.Entry<UUID, String>> top,
            Map.Entry<UUID, String> entry, int capacity,
            Comparator<Map.Entry<UUID, String>> order) {
        if (capacity == 0) return;
        if (top.size() < capacity) top.add(entry);
        else if (order.compare(entry, top.peek()) < 0) {
            top.remove();
            top.add(entry);
        }
    }
}
