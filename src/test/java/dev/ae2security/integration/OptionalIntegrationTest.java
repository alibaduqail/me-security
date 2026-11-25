package dev.ae2security.integration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.ae2security.security.AE2wtlibSecurityPatched;
import net.neoforged.fml.ModList;
import org.junit.jupiter.api.Test;

/** Forces optional targets through the mixin transformer when their pinned mod is installed. */
class OptionalIntegrationTest {
    @Test
    void ae2wtlibMagnetCompatibilityLoadsWhenPresent() {
        // Report "skipped" rather than a vacuous pass when the optional mod is absent.
        assumeTrue(ModList.get().isLoaded("ae2wtlib"), "AE2 Wireless Terminals is not installed");
        var target = assertDoesNotThrow(
                () -> Class.forName("de.mari_023.ae2wtlib.wct.magnet_card.MagnetHandler"));
        assertTrue(AE2wtlibSecurityPatched.class.isAssignableFrom(target),
                "AE2WTLib loaded without the required inventory-sync security patch");
        var shortcuts = assertDoesNotThrow(
                () -> Class.forName("de.mari_023.ae2wtlib.wct.CraftingTerminalHandler"));
        assertTrue(AE2wtlibSecurityPatched.class.isAssignableFrom(shortcuts),
                "AE2WTLib loaded without the required shortcut range patch");
    }
}
