/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/** Pure in-memory date boundary tests; no SQLite or Android alarms. */
public class ExpiredReminderAlarmPruneTest {

    private static EventReminder at(long id, DateTime date) {
        EventReminder reminder = new EventReminder(date, EventType.MEDICATION_INTAKE);
        reminder.setId(id);
        reminder.setNextTime(date);
        return reminder;
    }

    @Test
    public void snapshotContainsOnlyRowsOlderThanSqlCutoff() {
        DateTimeZone madrid = DateTimeZone.forID("Europe/Madrid");
        DateTime cutoff = new DateTime(2026, 10, 7, 0, 0, madrid);
        EventReminder expired = at(1, cutoff.minusMillis(1));
        EventReminder atBoundary = at(2, cutoff);
        EventReminder future = at(3, cutoff.plusMinutes(1));

        List<EventReminder> expiredRows = Agenda.expiredRemindersBefore(
                Arrays.asList(expired, atBoundary, future), cutoff);

        assertEquals(1, expiredRows.size());
        assertSame("The snapshot must preserve persisted reminder identity",
                expired, expiredRows.get(0));
        assertEquals(3, Arrays.asList(expired, atBoundary, future).size());
    }

    @Test
    public void oldIntakeSnoozedBeyondCutoffIsNotCancelledAsExpired() {
        DateTimeZone madrid = DateTimeZone.forID("Europe/Madrid");
        DateTime cutoff = new DateTime(2026, 10, 13, 0, 0, madrid);
        EventReminder snoozed = at(2001, cutoff.minusDays(3));
        snoozed.setNextTime(cutoff.plusHours(4));
        EventReminder originalPastAndExpired = at(2002, cutoff.minusDays(3));
        EventReminder missingNextTime = at(2003, cutoff.minusDays(3));
        missingNextTime.setNextTime(null);
        EventReminder atBoundary = at(2004, cutoff.minusDays(3));
        atBoundary.setNextTime(cutoff);
        List<EventReminder> expired = Agenda.expiredRemindersBefore(
                Arrays.asList(snoozed, originalPastAndExpired,
                        missingNextTime, atBoundary), cutoff);
        assertEquals("Only truly expired deliveries can lose their OS tokens", 2, expired.size());
        assertSame(originalPastAndExpired, expired.get(0));
        assertSame(missingNextTime, expired.get(1));
    }

    @Test
    public void emptySnapshotNeverInventsAnOsCancellation() {
        DateTime cutoff = DateTime.now().minusDays(1);
        assertEquals(0, Agenda.expiredRemindersBefore(
                Collections.emptyList(), cutoff).size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullPersistedReminderRowIsNotSilentlyAccepted() {
        Agenda.expiredRemindersBefore(
                Collections.singletonList(null), DateTime.now());
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullPersistedReminderDateIsNotSilentlyAccepted() {
        Agenda.expiredRemindersBefore(
                Collections.singletonList(new EventReminder()), DateTime.now());
    }
}
