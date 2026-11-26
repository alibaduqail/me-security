package dev.ae2security.client;

import java.util.List;
import java.util.Locale;
import java.util.function.ToIntFunction;

import dev.ae2security.network.PolicyEditResult;
import dev.ae2security.network.PolicySnapshot;
import net.minecraft.network.chat.Component;

final class SecurityScreenText {
    private SecurityScreenText() {
    }

    static Component playerTooltip(PolicySnapshot.PlayerEntry player) {
        return Component.translatable(
                player.trusted() ? "screen.ae2security.player_trusted" : "screen.ae2security.player_untrusted",
                player.name(), player.id().toString());
    }

    /**
     * Whether the selected player has a row on the shown page. Compared by UUID: the row and
     * the selection may carry different trust or permissions while an update is in flight.
     */
    static boolean isListed(List<PolicySnapshot.PlayerEntry> rows, PolicySnapshot.PlayerEntry selected) {
        for (var row : rows) {
            if (row.id().equals(selected.id())) return true;
        }
        return false;
    }

    /** The first candidate no wider than {@code maxWidth}, or the last one when none fits. */
    static String firstThatFits(List<String> candidates, int maxWidth, ToIntFunction<String> width) {
        for (var candidate : candidates) {
            if (width.applyAsInt(candidate) <= maxWidth) return candidate;
        }
        return candidates.get(candidates.size() - 1);
    }

    /** The access panel's message for an edit result: failures only, as a saved change needs no note. */
    static String editFailureKey(PolicyEditResult.Result result) {
        if (result == null || result == PolicyEditResult.Result.APPLIED) return null;
        return "screen.ae2security.edit_result." + result.name().toLowerCase(Locale.ROOT);
    }
}
