/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Pure time calculations, without alarms, databases or patient data. */
public class AgendaDailyUpdateTimingTest {

    private static final DateTimeZone MADRID = DateTimeZone.forID("Europe/Madrid");

    private static void expectNextMidnight(DateTime current, DateTime expected) {
        long actual = Agenda.nextDailyUpdateMillis(current);
        assertEquals(expected.getMillis(), actual);
        assertTrue("A new repeating alarm must never begin in the past",
                actual > current.getMillis());
    }

    @Test
    public void savedDateFromTodaySkipsDuplicateAgendaRebuild() {
        LocalDate today = new LocalDate(2026, 10, 8);
        assertFalse(Agenda.shouldRefreshAgenda(today, "20261008"));
    }

    @Test
    public void missingOrStaleUpdateDateTriggersAgendaRebuild() {
        LocalDate today = new LocalDate(2026, 10, 8);
        assertTrue(Agenda.shouldRefreshAgenda(today, null));
        assertTrue(Agenda.shouldRefreshAgenda(today, "20261007"));
        assertTrue(Agenda.shouldRefreshAgenda(today, "20261009"));
    }

    @Test
    public void corruptStoredUpdateDateDoesNotBlockReminderRebuild() {
        LocalDate today = new LocalDate(2026, 10, 8);
        assertTrue(Agenda.shouldRefreshAgenda(today, "not-a-date"));
        assertTrue(Agenda.shouldRefreshAgenda(today, "20260230"));
    }

    @Test
    public void successiveSpringMidnightsAreTwentyThreeHoursApart() {
        DateTime before = new DateTime(2026, 3, 28, 12, 0, MADRID);
        long first = Agenda.nextDailyUpdateMillis(before);
        long second = Agenda.nextDailyUpdateMillis(new DateTime(first, MADRID));

        assertEquals("DST spring-forward must not drift to 01:00 local",
                new DateTime(2026, 3, 30, 0, 0, MADRID).getMillis(), second);
        assertEquals(23L * 60 * 60 * 1000, second - first);
    }

    @Test
    public void successiveAutumnMidnightsAreTwentyFiveHoursApart() {
        DateTime before = new DateTime(2026, 10, 24, 12, 0, MADRID);
        long first = Agenda.nextDailyUpdateMillis(before);
        long second = Agenda.nextDailyUpdateMillis(new DateTime(first, MADRID));

        assertEquals("DST fall-back must not drift to 23:00 local",
                new DateTime(2026, 10, 26, 0, 0, MADRID).getMillis(), second);
        assertEquals(25L * 60 * 60 * 1000, second - first);
    }

    @Test
    public void appOpenedDuringDaySchedulesTomorrowNotPastMidnight() {
        expectNextMidnight(
                new DateTime(2026, 10, 8, 19, 54, MADRID),
                new DateTime(2026, 10, 9, 0, 0, MADRID));
    }

    @Test
    public void appOpenedJustAfterMidnightDoesNotSchedulePastTimestamp() {
        expectNextMidnight(
                new DateTime(2026, 10, 8, 0, 0, 1, MADRID),
                new DateTime(2026, 10, 9, 0, 0, MADRID));
    }

    @Test
    public void firstTriggerRespectsSpringDaylightSavingTransition() {
        expectNextMidnight(
                new DateTime(2026, 3, 29, 1, 30, MADRID),
                new DateTime(2026, 3, 30, 0, 0, MADRID));
    }

    @Test
    public void firstTriggerRespectsAutumnDaylightSavingTransition() {
        expectNextMidnight(
                new DateTime(2026, 10, 25, 2, 30, MADRID).withEarlierOffsetAtOverlap(),
                new DateTime(2026, 10, 26, 0, 0, MADRID));
    }
}
