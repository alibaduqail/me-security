package dev.ae2security.client;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import dev.ae2security.network.PolicyEditResult;
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

    @Test
    void selectionIsListedByIdEvenWhenItsRowIsStale() {
        var selected = new PolicySnapshot.PlayerEntry(
                UUID.fromString("404198aa-cc64-3021-9fdf-8766c0d0096c"), "Guest", true, 3);
        var staleRow = new PolicySnapshot.PlayerEntry(selected.id(), "Guest", false, 0);
        var other = new PolicySnapshot.PlayerEntry(
                UUID.fromString("0f0e3c8a-1b2d-3e4f-8a9b-0c1d2e3f4a5b"), "Other", false, 0);

        assertTrue(SecurityScreenText.isListed(List.of(other, staleRow), selected));
        assertFalse(SecurityScreenText.isListed(List.of(other), selected),
                "a selection kept after trusting moves it to another page is not on this one");
        assertFalse(SecurityScreenText.isListed(List.of(), selected));
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

    @Test
    void onlyFailedEditsGetAStatusLine() {
        assertNull(SecurityScreenText.editFailureKey(null));
        assertNull(SecurityScreenText.editFailureKey(PolicyEditResult.Result.APPLIED),
                "a saved change shows in the switches, not as a \"Changes saved\" note");
        assertEquals("screen.ae2security.edit_result.refresh",
                SecurityScreenText.editFailureKey(PolicyEditResult.Result.REFRESH));
        assertEquals("screen.ae2security.edit_result.denied",
                SecurityScreenText.editFailureKey(PolicyEditResult.Result.DENIED));
        assertEquals("screen.ae2security.edit_result.invalid",
                SecurityScreenText.editFailureKey(PolicyEditResult.Result.INVALID));
    }
}
