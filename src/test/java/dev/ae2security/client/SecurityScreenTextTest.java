package dev.ae2security.client;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.UUID;

import dev.ae2security.network.PolicySnapshot;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

class SecurityScreenTextTest {
    @Test
    void playerTooltipAcceptsOfflinePlayerUuid() {
        var uuid = UUID.fromString("404198aa-cc64-3021-9fdf-8766c0d0096c");
        var player = new PolicySnapshot.PlayerEntry(uuid, "Guest", false, 0);

        var tooltip = assertDoesNotThrow(() -> SecurityScreenText.playerTooltip(player));

        var translated = assertInstanceOf(TranslatableContents.class, tooltip.getContents());
        assertEquals(uuid.toString(), translated.getArgs()[1]);
    }
}
