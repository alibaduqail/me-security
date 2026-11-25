package dev.ae2security.client;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.UUID;

import dev.ae2security.network.PolicySnapshot;
import dev.ae2security.security.Permission;
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

    @Test
    void grantedCountIgnoresImpliedViewAndUnknownBits() {
        assertEquals(0, SecurityScreenText.grantedCount(Permission.VIEW.bit()));
        assertEquals(2, SecurityScreenText.grantedCount(
                Permission.VIEW.bit() | Permission.INSERT.bit() | Permission.COLLECT.bit()));
        assertEquals(Permission.editable().size(), SecurityScreenText.grantedCount(Permission.ALL));
        assertEquals(Permission.editable().size(), SecurityScreenText.grantedCount(-1));
    }

    @Test
    void accessSummaryCountsGrantsForTrustedPlayersOnly() {
        var id = UUID.fromString("404198aa-cc64-3021-9fdf-8766c0d0096c");
        var trusted = new PolicySnapshot.PlayerEntry(id, "Builder", true,
                Permission.VIEW.bit() | Permission.CRAFT.bit() | Permission.BUILD.bit());
        var guest = new PolicySnapshot.PlayerEntry(id, "Guest", false, Permission.ALL);

        var summary = assertInstanceOf(TranslatableContents.class,
                SecurityScreenText.accessSummary(trusted).getContents());
        assertEquals("screen.ae2security.summary.trusted", summary.getKey());
        assertEquals(2, summary.getArgs()[0]);
        assertEquals(Permission.editable().size(), summary.getArgs()[1]);

        var untrusted = assertInstanceOf(TranslatableContents.class,
                SecurityScreenText.accessSummary(guest).getContents());
        assertEquals("screen.ae2security.summary.untrusted", untrusted.getKey());
    }

    @Test
    void transferPromptFallsBackToShorterFormsThatFit() {
        var forms = java.util.List.of("Type Alex_Builder to confirm", "Type Alex_Builder", "Alex_Builder");
        java.util.function.ToIntFunction<String> width = String::length;

        assertEquals("Type Alex_Builder to confirm", SecurityScreenText.firstThatFits(forms, 40, width));
        assertEquals("Type Alex_Builder", SecurityScreenText.firstThatFits(forms, 20, width));
        assertEquals("Alex_Builder", SecurityScreenText.firstThatFits(forms, 12, width));
        assertEquals("Alex_Builder", SecurityScreenText.firstThatFits(forms, 3, width),
                "the last form is kept, for the caller to clip, when nothing fits");
    }
}
