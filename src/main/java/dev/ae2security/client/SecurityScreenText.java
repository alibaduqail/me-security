package dev.ae2security.client;

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
}
