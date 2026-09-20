package dev.ae2security.security;

import java.util.List;

/** Stable persisted bits. Never reorder or reuse these values. */
public enum Permission {
    VIEW(1), INSERT(2), EXTRACT(4), CRAFT(8), BUILD(16);

    public static final int ALL = 31;
    private static final List<Permission> EDITABLE = List.of(INSERT, EXTRACT, CRAFT, BUILD);
    private final int bit;

    Permission(int bit) { this.bit = bit; }
    public int bit() { return bit; }

    /** Permissions exposed in the owner screen. View is implied by being trusted. */
    public static List<Permission> editable() { return EDITABLE; }

    public static int normalize(int mask) {
        return (mask & ALL) | VIEW.bit;
    }

    public boolean in(int mask) { return (mask & bit) != 0; }
}
