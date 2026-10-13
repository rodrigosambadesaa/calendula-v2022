/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import android.app.PendingIntent;
import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;
import es.usc.citius.servando.calendula.util.PendingIntentFlags;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/** Real Android PendingIntent retirement with synthetic expired IDs; no database. */
@RunWith(AndroidJUnit4.class)
public class ExpiredReminderPruneSmokeTest {

    @Test
    public void sqlitePruningPreservesSnoozedOldMedicationReminder() throws Exception {
        assertTrue(DB.initialized);
        DateTime cutoff = DateTime.now().withTimeAtStartOfDay().minusDays(1);
        EventReminder snoozed = new EventReminder(
                cutoff.minusHours(4), EventType.MEDICATION_INTAKE);
        snoozed.setNextTime(cutoff.plusDays(2));
        EventReminder expired = new EventReminder(
                cutoff.minusHours(3), EventType.MEDICATION_INTAKE);
        expired.setNextTime(cutoff.minusHours(1));
        EventReminder noNextTime = new EventReminder(
                cutoff.minusHours(2), EventType.MEDICATION_INTAKE);
        noNextTime.setNextTime(null);
        EventReminder atBoundary = new EventReminder(
                cutoff.minusHours(1), EventType.MEDICATION_INTAKE);
        atBoundary.setNextTime(cutoff);
        EventReminder[] fixture = {snoozed, expired, noNextTime, atBoundary};
        try {
            for (EventReminder reminder : fixture) {
                assertEquals(1, DB.eventReminders().create(reminder));
                assertNotNull(reminder.getId());
            }
            assertEquals("Only deliveries fully older than the cutoff are removed",
                    2, DB.eventReminders().removeOlderThan(cutoff));
            assertNotNull("Future snooze must remain persisted",
                    DB.eventReminders().findById(snoozed.getId()));
            assertNotNull("Boundary delivery must remain persisted",
                    DB.eventReminders().findById(atBoundary.getId()));
            assertNull(DB.eventReminders().findById(expired.getId()));
            assertNull(DB.eventReminders().findById(noNextTime.getId()));
            // The Android cancellation snapshot must select precisely those
            // deleted row identities, never a retained snooze/valid token.
            List<EventReminder> retired = Agenda.expiredRemindersBefore(
                    Arrays.asList(fixture), cutoff);
            assertEquals(2, retired.size());
            assertTrue(retired.contains(expired));
            assertTrue(retired.contains(noNextTime));
        } finally {
            for (EventReminder reminder : fixture) {
                if (reminder.getId() != null
                        && DB.eventReminders().findById(reminder.getId()) != null) {
                    DB.eventReminders().remove(reminder);
                }
            }
        }
    }

    @Test
    public void onlyExpiredReminderAlarmIsRevokedAfterSyntheticCommit() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DateTime cutoff = DateTime.now().withTimeAtStartOfDay().minusDays(1);

        EventReminder expired = new EventReminder(cutoff.minusMinutes(5),
                EventType.MEDICATION_INTAKE);
        expired.setId(9000001301L);
        EventReminder retained = new EventReminder(cutoff.plusMinutes(5),
                EventType.MEDICATION_INTAKE);
        retained.setId(9000001302L);

        PendingIntent expiredToken = PendingIntent.getBroadcast(context, 0,
                Agenda.reminderBroadcastIntent(context, expired),
                PendingIntentFlags.immutable(PendingIntent.FLAG_UPDATE_CURRENT));
        PendingIntent retainedToken = PendingIntent.getBroadcast(context, 0,
                Agenda.reminderBroadcastIntent(context, retained),
                PendingIntentFlags.immutable(PendingIntent.FLAG_UPDATE_CURRENT));
        try {
            List<EventReminder> toCancel = Agenda.expiredRemindersBefore(
                    Arrays.asList(expired, retained), cutoff);
            assertEquals(1, toCancel.size());
            for (EventReminder reminder : toCancel) {
                // Same OS retirement path used AFTER daily SQLite commit.
                Agenda.instance().cancelAlarm(context, reminder);
            }
            assertNull("Expired token must be retired",
                    PendingIntent.getBroadcast(context, 0,
                            Agenda.reminderBroadcastIntent(context, expired),
                            PendingIntentFlags.immutable(PendingIntent.FLAG_NO_CREATE)));
            assertNotNull("Retained token must survive",
                    PendingIntent.getBroadcast(context, 0,
                            Agenda.reminderBroadcastIntent(context, retained),
                            PendingIntentFlags.immutable(PendingIntent.FLAG_NO_CREATE)));
        } finally {
            expiredToken.cancel();
            retainedToken.cancel();
        }
    }
}
