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

    @Test void agesAreCoarseAndNeverNegative() {
        long minute = 60_000L;
        assertEquals(new SecurityAlarm.Ago("moments", 0), SecurityAlarm.ago(-5_000));
        assertEquals(new SecurityAlarm.Ago("moments", 0), SecurityAlarm.ago(minute - 1));
        assertEquals(new SecurityAlarm.Ago("minutes", 1), SecurityAlarm.ago(minute));
        assertEquals(new SecurityAlarm.Ago("minutes", 59), SecurityAlarm.ago(60 * minute - 1));
        assertEquals(new SecurityAlarm.Ago("hours", 1), SecurityAlarm.ago(60 * minute));
        assertEquals(new SecurityAlarm.Ago("hours", 47), SecurityAlarm.ago(48 * 60 * minute - 1));
        assertEquals(new SecurityAlarm.Ago("days", 2), SecurityAlarm.ago(48 * 60 * minute));
    }

    @Test void theOwnerButtonVisitsEveryModeAndSavedNamesReadBack() {
        var seen = java.util.EnumSet.noneOf(AlarmMode.class);
        var mode = AlarmMode.DEFAULT;
        for (int i = 0; i < AlarmMode.values().length; i++) {
            seen.add(mode);
            assertEquals(mode, AlarmMode.byId(mode.id()));
            mode = mode.next();
        }
        assertEquals(java.util.EnumSet.allOf(AlarmMode.class), seen);
        assertEquals(AlarmMode.DEFAULT, mode, "the button comes back round to where it started");
        assertNull(AlarmMode.byId("siren"));
        assertTrue(AlarmMode.ALERT.alerts() && !AlarmMode.ALERT.redstone());
        assertTrue(AlarmMode.REDSTONE.alerts() && AlarmMode.REDSTONE.redstone());
        assertFalse(AlarmMode.OFF.alerts() || AlarmMode.OFF.redstone());
    }
}
