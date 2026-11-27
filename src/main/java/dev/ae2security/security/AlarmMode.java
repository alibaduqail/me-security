package dev.ae2security.security;

import java.util.Locale;

/**
 * What a Security Terminal does when a player with no standing on its network is denied.
 * The names are saved and the order is sent over the network: never rename or reorder these values.
 */
public enum AlarmMode {
    /** Nothing: denied attempts are neither reported nor signalled. */
    OFF,
    /** The owner is told, at once when online and otherwise at their next login. */
    ALERT,
    /** As {@link #ALERT}, and the terminal's block emits a redstone pulse. */
    REDSTONE;

    /** Alerts cost the owner nothing; redstone can drive machines, so the owner switches it on deliberately. */
    public static final AlarmMode DEFAULT = ALERT;

    public boolean alerts() { return this != OFF; }
    public boolean redstone() { return this == REDSTONE; }

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
