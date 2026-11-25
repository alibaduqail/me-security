package dev.ae2security.client;

import java.util.List;
import java.util.function.ToIntFunction;

import dev.ae2security.network.PolicySnapshot;
import dev.ae2security.security.Permission;
import net.minecraft.network.chat.Component;

final class SecurityScreenText {
    private SecurityScreenText() {
    }

    static Component playerTooltip(PolicySnapshot.PlayerEntry player) {
        return Component.translatable(
                player.trusted() ? "screen.ae2security.player_trusted" : "screen.ae2security.player_untrusted",
                player.name(), player.id().toString());
    }

    /** How many of the permissions shown in the screen the mask grants; View is implied and not counted. */
    static int grantedCount(int mask) {
        int count = 0;
        for (var permission : Permission.editable()) {
            if (permission.in(mask)) count++;
        }
        return count;
    }

    /** The second line under the selected player's name: their trust and how much they hold. */
    static Component accessSummary(PolicySnapshot.PlayerEntry player) {
        return player.trusted()
                ? Component.translatable("screen.ae2security.summary.trusted",
                        grantedCount(player.permissions()), Permission.editable().size())
                : Component.translatable("screen.ae2security.summary.untrusted");
    }

    /** The first candidate no wider than {@code maxWidth}, or the last one when none fits. */
    static String firstThatFits(List<String> candidates, int maxWidth, ToIntFunction<String> width) {
        for (var candidate : candidates) {
            if (width.applyAsInt(candidate) <= maxWidth) return candidate;
        }
        return candidates.get(candidates.size() - 1);
    }
}
