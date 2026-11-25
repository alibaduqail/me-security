package dev.ae2security.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.ae2security.client.PendingEdits.Outcome;
import dev.ae2security.network.PolicyEditResult;
import dev.ae2security.network.PolicyEditResult.Result;
import org.junit.jupiter.api.Test;

class PendingEditsTest {
    private static final int MENU = 4;
    private final PendingEdits edits = new PendingEdits();

    private static PolicyEditResult ack(int requestId, Result result) {
        return new PolicyEditResult(MENU, requestId, result);
    }

    @Test
    void nothingIsIssuedWhileABlockingEditIsUnacknowledged() {
        int remove = edits.begin(true);

        assertNotEquals(PendingEdits.BLOCKED, remove);
        assertTrue(edits.pending());
        assertEquals(PendingEdits.BLOCKED, edits.begin(false), "a permission toggle must wait");
        assertEquals(PendingEdits.BLOCKED, edits.begin(true), "a second trust change must wait");

        assertEquals(Outcome.COMPLETED, edits.accept(ack(remove, Result.APPLIED)));
        assertFalse(edits.pending());
        assertEquals(Result.APPLIED, edits.status());

        assertNotEquals(PendingEdits.BLOCKED, edits.begin(false));
        assertNull(edits.status(), "a new edit clears the previous status line");
    }

    @Test
    void togglesOverlapAndTheirAcknowledgementsChangeNothing() {
        int first = edits.begin(false);
        int second = edits.begin(false);

        assertNotEquals(PendingEdits.BLOCKED, first);
        assertNotEquals(first, second);
        assertFalse(edits.pending());
        assertEquals(Outcome.IGNORED, edits.accept(ack(second, Result.REFRESH)));
        assertNull(edits.status());
    }

    @Test
    void acknowledgementOfAnEarlierToggleDoesNotReleaseTheAwaitedEdit() {
        int toggle = edits.begin(false);
        int remove = edits.begin(true);

        assertEquals(Outcome.IGNORED, edits.accept(ack(toggle, Result.APPLIED)));
        assertTrue(edits.pending());
        assertEquals(Outcome.COMPLETED, edits.accept(ack(remove, Result.INVALID)));
        assertFalse(edits.pending());
        assertEquals(Result.INVALID, edits.status());
    }

    /** The menu keeps one result: a later acknowledgement can replace the awaited one between ticks. */
    @Test
    void newerAcknowledgementReleasesAnEditWhoseOwnAnswerWasOverwritten() {
        int remove = edits.begin(true);

        assertEquals(Outcome.SUPERSEDED, edits.accept(ack(remove + 1, Result.REFRESH)));

        assertFalse(edits.pending(), "the screen must not stay on Saving...");
        assertNull(edits.status(), "the lost answer's outcome is unknown, so none is claimed");
        assertNotEquals(PendingEdits.BLOCKED, edits.begin(true));
    }

    @Test
    void eachResultObjectIsProcessedOnce() {
        int trust = edits.begin(true);
        var answer = ack(trust, Result.APPLIED);

        assertEquals(Outcome.IGNORED, edits.accept(null));
        assertEquals(Outcome.COMPLETED, edits.accept(answer));

        int next = edits.begin(true);
        assertEquals(Outcome.IGNORED, edits.accept(answer), "the menu still holds the old answer");
        assertTrue(edits.pending());
        assertEquals(Outcome.COMPLETED, edits.accept(ack(next, Result.APPLIED)));
    }
}
