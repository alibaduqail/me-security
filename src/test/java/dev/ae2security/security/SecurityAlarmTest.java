package dev.ae2security.security;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class SecurityAlarmTest {
    private static final int INTERVAL = SecurityAlarm.REPORT_INTERVAL_TICKS;

    @Test void oneReportPerIntruderAndTerminalInEachInterval() {
        var terminal = UUID.randomUUID();
        var intruder = UUID.randomUUID();
        assertTrue(SecurityAlarm.due(terminal, intruder, 100));
        assertFalse(SecurityAlarm.due(terminal, intruder, 100));
        assertFalse(SecurityAlarm.due(terminal, intruder, 100 + INTERVAL - 1));
        assertTrue(SecurityAlarm.due(terminal, intruder, 100 + INTERVAL));
        assertFalse(SecurityAlarm.due(terminal, intruder, 101 + INTERVAL), "the interval restarts with each report");
    }

    @Test void anotherIntruderOrAnotherTerminalIsReportedAtOnce() {
        var terminal = UUID.randomUUID();
        var intruder = UUID.randomUUID();
        assertTrue(SecurityAlarm.due(terminal, intruder, 100));
        assertTrue(SecurityAlarm.due(terminal, UUID.randomUUID(), 100));
        assertTrue(SecurityAlarm.due(UUID.randomUUID(), intruder, 100));
    }

    @Test void aTickCountThatWentBackwardsDoesNotSilenceTheAlarm() {
        var terminal = UUID.randomUUID();
        var intruder = UUID.randomUUID();
        assertTrue(SecurityAlarm.due(terminal, intruder, 50_000));
        assertTrue(SecurityAlarm.due(terminal, intruder, 10), "a restarted server counts ticks from zero again");
    }

    @Test void theReportTableStaysBoundedAndKeepsReporting() {
        SecurityAlarm.forgetReports();
        var terminal = UUID.randomUUID();
        for (int i = 0; i < 5_000; i++) assertTrue(SecurityAlarm.due(terminal, UUID.randomUUID(), 100));
        var intruder = UUID.randomUUID();
        assertTrue(SecurityAlarm.due(terminal, intruder, 100));
        assertFalse(SecurityAlarm.due(terminal, intruder, 101));
        SecurityAlarm.forgetReports();
    }

    @Test void anEmitterWatchesStrangersUnlessSetToEveryone() {
        assertEquals(EmitterTrigger.STRANGERS, EmitterTrigger.DEFAULT);
        assertTrue(EmitterTrigger.STRANGERS.reactsTo(true));
        assertFalse(EmitterTrigger.STRANGERS.reactsTo(false), "a trusted player lacking a permission is not a stranger");
        assertTrue(EmitterTrigger.EVERYONE.reactsTo(true));
        assertTrue(EmitterTrigger.EVERYONE.reactsTo(false));
        for (var trigger : EmitterTrigger.values()) {
            assertEquals(trigger, EmitterTrigger.byId(trigger.id()));
            assertNotEquals(trigger, trigger.next());
            assertEquals(trigger, trigger.next().next());
        }
        assertEquals(EmitterTrigger.DEFAULT, EmitterTrigger.byId(""), "a part saved before the setting existed");
        assertEquals(EmitterTrigger.DEFAULT, EmitterTrigger.byId("nobody"));
    }
}
