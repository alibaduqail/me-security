package dev.ae2security.security;

import java.util.Locale;

/**
 * Whose denied actions trigger a Security Emitter. The names are saved: never rename these values.
 */
public enum EmitterTrigger {
    /** Only players with no standing on the network: neither its owner nor trusted. */
    STRANGERS,
    /** Anyone denied, including a trusted player who lacks the permission they tried to use. */
    EVERYONE;

    public static final EmitterTrigger DEFAULT = STRANGERS;

    public boolean reactsTo(boolean stranger) { return stranger || this == EVERYONE; }

    public EmitterTrigger next() { return this == STRANGERS ? EVERYONE : STRANGERS; }

    public String id() { return name().toLowerCase(Locale.ROOT); }

    /** Returns {@link #DEFAULT} for a name this version does not know. */
    public static EmitterTrigger byId(String id) {
        for (var trigger : values()) {
            if (trigger.id().equals(id)) return trigger;
        }
        return DEFAULT;
    }
}
