package dev.ae2security.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OwnershipConfirmationTest {
    @Test void requiresTheTargetPlayersName() {
        assertTrue(OwnershipConfirmation.matches("alchemist22", "alchemist22"));
        assertTrue(OwnershipConfirmation.matches("alchemist22", "  Alchemist22  "));
        assertFalse(OwnershipConfirmation.matches("alchemist22", "another_player"));
        assertFalse(OwnershipConfirmation.matches("alchemist22", ""));
    }
}
