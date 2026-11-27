package dev.ae2security.security;

import java.util.Locale;

/**
 * Whether a Security Terminal tells its owner when a player with no standing on its network is denied.
 * Redstone output is a separate part, the Security Emitter. The names are saved and the order is sent
 * over the network: never rename or reorder these values.
 */
public enum AlarmMode {
    /** Nothing: denied attempts are neither reported nor signalled. */
    OFF,
    /** The owner is told, at once when online and otherwise at their next login. */
    ALERT;

    /** Alerts cost the owner nothing, so existing and new terminals start with them on. */
    public static final AlarmMode DEFAULT = ALERT;

    public boolean alerts() { return this != OFF; }

    /** The mode the owner's toolbar button switches to next. */
    public AlarmMode next() {
        var modes = values();
        return modes[(ordinal() + 1) % modes.length];
    }

    public String id() { return name().toLowerCase(Locale.ROOT); }

    /** Returns null for a name this version does not know. */
    public static AlarmMode byId(String id) {
        for (var mode : values()) {
            if (mode.id().equals(id)) return mode;
        }
        return null;
    }
}
