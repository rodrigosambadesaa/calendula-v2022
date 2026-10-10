/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import android.app.PendingIntent;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;
import es.usc.citius.servando.calendula.util.PendingIntentFlags;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Real SQLite, synthetic records only, no actual medication delivery. */
@RunWith(AndroidJUnit4.class)
public class NoReminderResurrectionSmokeTest {

    @Test
    public void staleOrInvalidNotificationActionsCannotReportSuccess() {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertFalse("Missing ID must never be reported cancelled",
                Agenda.instance().cancelReminder(context, -1L));
        assertFalse("Missing ID must never be reported delayed",
                Agenda.instance().delayReminder(context, -1L));
        assertFalse("Unknown persisted row must not be reported cancelled",
                Agenda.instance().cancelReminder(context, Long.MAX_VALUE));
        assertFalse("Unknown persisted row must not be reported delayed",
                Agenda.instance().delayReminder(context, Long.MAX_VALUE));
        assertFalse("Null ID cannot claim a successful cancel",
                Agenda.instance().cancelReminder(context, (Long) null));
        assertFalse("Null ID cannot claim a successful delay",
                Agenda.instance().delayReminder(context, (Long) null));
    }

    @Test
    public void inactiveDoseCannotBeReportedDelayedOrModifyPersistedReminder() {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DateTime due = DateTime.now().plusHours(9).withMillisOfSecond(0);
        EventReminder orphan = new EventReminder(due, EventType.MEDICATION_INTAKE);
        orphan.setNextTime(due);
        try {
            // This reminder deliberately has no active EventInstance behind it.
            DB.eventReminders().save(orphan);
            assertNotNull(orphan.getId());
            assertFalse("No pending intake means no successful snooze",
                    Agenda.instance().delayReminder(context, orphan.getId()));
            assertFalse("No pending intake can be falsely reported cancelled",
                    Agenda.instance().cancelReminder(context, orphan.getId()));
            assertNotNull("Inactive orphan cleanup remains a separate alarm reconciliation task",
                    DB.eventReminders().findById(orphan.getId()));
            assertEquals("A stale action cannot silently rewrite delivery time",
                    due, DB.eventReminders().findById(orphan.getId()).getNextTime());
        } finally {
            if (orphan.getId() != null && DB.eventReminders().findById(orphan.getId()) != null) {
                DB.eventReminders().remove(orphan);
            }
        }
    }

    @Test
    public void removedOrRetimedPersistedReminderCannotBeRepeatedFromStaleAlarm() {
        assertTrue(DB.initialized);
        DateTime time = DateTime.now().plusHours(16).withMillisOfSecond(0);
        EventInstance event = new EventInstance(time, EventType.MEDICATION_INTAKE);
        EventReminder reminder = new EventReminder(time, EventType.MEDICATION_INTAKE);
        reminder.setNextTime(time);
        try {
            DB.eventInstances().save(event);
            DB.eventReminders().save(reminder);
            assertNotNull(event.getId());
            assertNotNull(reminder.getId());
            assertTrue(Agenda.instance().isCurrentPendingReminder(reminder));

            EventReminder persisted = DB.eventReminders().findById(reminder.getId());
            assertNotNull(persisted);
            persisted.setNextTime(time.plusMinutes(7));
            DB.eventReminders().save(persisted);
            assertFalse("Stale in-memory time must not override user delay",
                    Agenda.instance().isCurrentPendingReminder(reminder));

            DB.eventReminders().remove(persisted);
            assertFalse("Deleted reminder must never be reconstructed on alarm receive",
                    Agenda.instance().isCurrentPendingReminder(reminder));
            assertNotNull("The event row is not silently deleted by this check",
                    DB.eventInstances().findById(event.getId()));
        } finally {
            if (reminder.getId() != null
                    && DB.eventReminders().findById(reminder.getId()) != null) {
                DB.eventReminders().remove(DB.eventReminders().findById(reminder.getId()));
            }
            if (event.getId() != null
                    && DB.eventInstances().findById(event.getId()) != null) {
                DB.eventInstances().remove(event);
            }
        }
    }

    /**
     * Genuine SQLite abort in the delay update. The previous OS token and
     * caller's in-memory time must remain intact, and retry must succeed.
     */
    @Test
    public void failedSqliteDelayKeepsPreviousReminderAndSuccessfulRetryPersistsFirst() {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SQLiteDatabase sqlite = DB.helper().getWritableDatabase();
        final String trigger = "ci_test_reminder_delay_abort";
        DateTime originalTime = DateTime.now().plusHours(18).withMillisOfSecond(0);
        EventInstance event = new EventInstance(originalTime, EventType.MEDICATION_INTAKE);
        EventReminder reminder = new EventReminder(originalTime, EventType.MEDICATION_INTAKE);
        reminder.setNextTime(originalTime);
        sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
        try {
            DB.eventInstances().save(event);
            DB.eventReminders().save(reminder);
            assertNotNull(event.getId());
            assertNotNull(reminder.getId());
            Agenda.instance().setAlarm(context, reminder);
            PendingIntent token = PendingIntent.getBroadcast(context, 0,
                    Agenda.reminderBroadcastIntent(context, reminder),
                    PendingIntentFlags.immutable(PendingIntent.FLAG_NO_CREATE));
            assertNotNull(token);

            sqlite.execSQL("CREATE TRIGGER " + trigger
                    + " BEFORE UPDATE ON EventReminders WHEN OLD._id = " + reminder.getId()
                    + " BEGIN SELECT RAISE(ABORT, 'synthetic reminder delay failure'); END;");
            boolean rejected = false;
            try {
                Agenda.instance().delayReminder(context, reminder, 600);
            } catch (RuntimeException expected) {
                rejected = true;
            }
            assertTrue("Real SQLite error must be propagated to caller", rejected);
            assertEquals("The original persisted delivery time must survive rollback",
                    originalTime, DB.eventReminders().findById(reminder.getId()).getNextTime());
            assertEquals("The caller model cannot claim a failed postpone",
                    originalTime, reminder.getNextTime());
            assertNotNull("Failed SQL must not revoke the existing Android token",
                    PendingIntent.getBroadcast(context, 0,
                            Agenda.reminderBroadcastIntent(context, reminder),
                            PendingIntentFlags.immutable(PendingIntent.FLAG_NO_CREATE)));

            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            assertTrue("Successful SQL commit and alarm setup should report success",
                    Agenda.instance().delayReminder(context, reminder, 600));
            DateTime committedTime = DB.eventReminders().findById(reminder.getId()).getNextTime();
            assertTrue("The new future time must be persisted before rescheduling",
                    committedTime.isAfter(originalTime.minusHours(18).plusMinutes(9)));
            assertEquals(committedTime, reminder.getNextTime());
            assertNotNull(PendingIntent.getBroadcast(context, 0,
                    Agenda.reminderBroadcastIntent(context, reminder),
                    PendingIntentFlags.immutable(PendingIntent.FLAG_NO_CREATE)));
        } finally {
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            if (reminder.getId() != null) {
                Agenda.instance().cancelAlarm(context, reminder);
                if (DB.eventReminders().findById(reminder.getId()) != null) {
                    DB.eventReminders().remove(reminder);
                }
            }
            if (event.getId() != null && DB.eventInstances().findById(event.getId()) != null) {
                DB.eventInstances().remove(event);
            }
        }
    }
}
