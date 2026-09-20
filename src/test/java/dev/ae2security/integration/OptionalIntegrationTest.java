package dev.ae2security.integration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.ae2security.security.AE2wtlibSecurityPatched;
import net.neoforged.fml.ModList;
import org.junit.jupiter.api.Test;

/** Forces optional targets through the mixin transformer when their pinned mod is installed. */
class OptionalIntegrationTest {
    @Test
    void ae2wtlibMagnetCompatibilityLoadsWhenPresent() {
        if (ModList.get().isLoaded("ae2wtlib")) {
            var target = assertDoesNotThrow(
                    () -> Class.forName("de.mari_023.ae2wtlib.wct.magnet_card.MagnetHandler"));
            assertTrue(AE2wtlibSecurityPatched.class.isAssignableFrom(target),
                    "AE2WTLib loaded without the required inventory-sync security patch");
        }
    }
}
