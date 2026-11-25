package dev.ae2security.security;

import java.util.Objects;

/**
 * Decides what a Security Terminal identity found at a location means. Copying block-entity data
 * (WorldEdit, creative pick-block, structure templates, /clone) duplicates the identity saved on the part,
 * while legitimate movers (Spatial IO, contraptions) take the part out of its old location first. The
 * recorded canonical location tells the two apart without ever loading a chunk.
 */
public final class TerminalIdentity {
    private TerminalIdentity() {}

    public enum Placement {
        /** No location is recorded yet; this instance claims it. */
        RECORD,
        /** This instance is at the recorded location. */
        SAME,
        /** The recorded location is loaded and still holds the identity: this instance is a copy. */
        CLONE,
        /** The recorded location is loaded and no longer holds the identity: the part was moved here. */
        MOVE,
        /** The recorded location is not loaded, so a copy cannot be told from a move. */
        AMBIGUOUS
    }

    /**
     * @param recorded the identity's canonical location, or null when unknown
     * @param current where this instance is
     * @param recordedLoaded whether the recorded location's chunk is loaded (ignored unless it differs)
     * @param recordedHoldsSameId whether another part with the same identity is at the recorded location
     */
    public static Placement classify(TerminalLocation recorded, TerminalLocation current,
            boolean recordedLoaded, boolean recordedHoldsSameId) {
        Objects.requireNonNull(current);
        if (recorded == null) return Placement.RECORD;
        if (recorded.equals(current)) return Placement.SAME;
        if (!recordedLoaded) return Placement.AMBIGUOUS;
        return recordedHoldsSameId ? Placement.CLONE : Placement.MOVE;
    }

    /**
     * Whether removing this instance may retire the shared identity. Only the canonical instance (or one
     * whose canonical location is unknown or provably vacated) may; a copy must leave the identity to the
     * instance that may still be protecting another network.
     */
    public static boolean shouldRetire(TerminalLocation recorded, TerminalLocation current,
            boolean recordedLoaded, boolean recordedHoldsSameId) {
        return switch (classify(recorded, current, recordedLoaded, recordedHoldsSameId)) {
            case RECORD, SAME, MOVE -> true;
            case CLONE, AMBIGUOUS -> false;
        };
    }
}
