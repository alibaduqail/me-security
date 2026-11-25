package dev.ae2security.client;

import java.util.List;
import java.util.function.ToIntFunction;

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

    /** The first candidate no wider than {@code maxWidth}, or the last one when none fits. */
    static String firstThatFits(List<String> candidates, int maxWidth, ToIntFunction<String> width) {
        for (var candidate : candidates) {
            if (width.applyAsInt(candidate) <= maxWidth) return candidate;
        }
        return candidates.get(candidates.size() - 1);
    }
}
