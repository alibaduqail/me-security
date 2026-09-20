package dev.ae2security.security;

/** Shared validation for the typed ownership-transfer confirmation. */
public final class OwnershipConfirmation {
    private OwnershipConfirmation() {}

    public static boolean matches(String playerName, String confirmation) {
        return playerName != null && confirmation != null
                && playerName.equalsIgnoreCase(confirmation.strip());
    }
}
