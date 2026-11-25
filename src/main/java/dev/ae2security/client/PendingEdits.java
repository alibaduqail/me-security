package dev.ae2security.client;

import dev.ae2security.network.PolicyEditResult;

/**
 * Request bookkeeping for policy edits, kept free of widgets so it can be unit tested.
 * <p>
 * The menu only remembers the latest acknowledgement, so an earlier one can be overwritten
 * before the screen ticks. Nothing is issued while a blocking edit is outstanding, and an
 * acknowledgement newer than the awaited one still releases the screen.
 */
final class PendingEdits {
    enum Outcome {
        /** Already processed, or not related to the awaited edit. */
        IGNORED,
        /** The awaited edit was answered; {@link #status()} holds its result. */
        COMPLETED,
        /** A later edit was answered, so the awaited answer was lost; its result is unknown. */
        SUPERSEDED
    }

    static final int BLOCKED = 0;

    private int lastRequestId;
    private int awaitedRequestId = BLOCKED;
    private PolicyEditResult processed;
    private PolicyEditResult.Result status;

    boolean pending() {
        return awaitedRequestId != BLOCKED;
    }

    /** Result of the last completed blocking edit, until the next edit is issued. */
    PolicyEditResult.Result status() {
        return status;
    }

    /**
     * Issues the id for a new edit, or {@link #BLOCKED} while a blocking edit is outstanding.
     *
     * @param blocking whether further edits must wait for this one to be acknowledged
     */
    int begin(boolean blocking) {
        if (pending()) {
            return BLOCKED;
        }
        if (++lastRequestId == BLOCKED) {
            lastRequestId++;
        }
        if (blocking) {
            awaitedRequestId = lastRequestId;
        }
        status = null;
        return lastRequestId;
    }

    Outcome accept(PolicyEditResult result) {
        if (result == null || result == processed) {
            return Outcome.IGNORED;
        }
        processed = result;
        // The server answers edits in order, so anything at or past the awaited id means the
        // awaited edit has been handled. Wrap-safe comparison, as ids only ever count up.
        if (!pending() || result.requestId() - awaitedRequestId < 0) {
            return Outcome.IGNORED;
        }
        boolean awaited = result.requestId() == awaitedRequestId;
        awaitedRequestId = BLOCKED;
        status = awaited ? result.result() : null;
        return awaited ? Outcome.COMPLETED : Outcome.SUPERSEDED;
    }
}
