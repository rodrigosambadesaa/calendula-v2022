/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import android.app.PendingIntent;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;
import es.usc.citius.servando.calendula.util.PendingIntentFlags;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Genuine SQLite and AlarmManager, disposable synthetic reminder only. */
@RunWith(AndroidJUnit4.class)
public class PublicReminderPostCommitSmokeTest {

    @Test
    public void supersededAlarmBroadcastNeverDeliversFutureDoseAndRearmsItsCurrentTime() {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DateTime currentDelivery = DateTime.now().plusMinutes(35).withMillisOfSecond(0);
        EventInstance pending = new EventInstance(currentDelivery, EventType.MEDICATION_INTAKE);
        EventReminder persisted = new EventReminder(currentDelivery, EventType.MEDICATION_INTAKE);
        persisted.setNextTime(currentDelivery);
        try {
            DB.eventInstances().save(pending);
            DB.eventReminders().save(persisted);
            assertNotNull(persisted.getId());
            assertNull("No OS alarm has been registered yet for this fixture",
                    PendingIntent.getBroadcast(context, 0,
                            Agenda.reminderBroadcastIntent(context, persisted),
                            PendingIntentFlags.immutable(PendingIntent.FLAG_NO_CREATE)));

            // Emulate an already queued alarm from the superseded delivery.
            // The latest persisted NextTime is still in the future.
            Agenda.instance().onReceiveAlarm(context, persisted.getId());
            assertEquals("A stale delivery cannot rewrite the time chosen by the user",
                    currentDelivery, DB.eventReminders().findById(persisted.getId()).getNextTime());
            assertTrue("A premature callback cannot mark a medication completed",
                    !DB.eventInstances().findById(pending.getId()).completed());
            assertNotNull("The current, future reminder must be rearmed instead of delivered early",
                    PendingIntent.getBroadcast(context, 0,
                            Agenda.reminderBroadcastIntent(context, persisted),
                            PendingIntentFlags.immutable(PendingIntent.FLAG_NO_CREATE)));
        } finally {
            if (persisted.getId() != null) {
                Agenda.instance().cancelAlarm(context, persisted);
                if (DB.eventReminders().findById(persisted.getId()) != null) {
                    DB.eventReminders().remove(persisted);
                }
            }
            if (pending.getId() != null && DB.eventInstances().findById(pending.getId()) != null) {
                DB.eventInstances().remove(pending);
            }
        }
    }

    @Test
    public void prematureOrphanBroadcastDoesNotCreateAnAndroidAlarm() {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DateTime future = DateTime.now().plusMinutes(40).withMillisOfSecond(0);
        EventReminder orphan = new EventReminder(future, EventType.MEDICATION_INTAKE);
        orphan.setNextTime(future);
        try {
            DB.eventReminders().save(orphan);
            Agenda.instance().onReceiveAlarm(context, orphan.getId());
            assertNull("A reminder with no pending intake must never rearm",
                    PendingIntent.getBroadcast(context, 0,
                            Agenda.reminderBroadcastIntent(context, orphan),
                            PendingIntentFlags.immutable(PendingIntent.FLAG_NO_CREATE)));
            assertNull("The old orphan cleanup must delete the invalid reminder",
                    DB.eventReminders().findById(orphan.getId()));
        } finally {
            if (orphan.getId() != null) {
                Agenda.instance().cancelAlarm(context, orphan);
                if (DB.eventReminders().findById(orphan.getId()) != null) {
                    DB.eventReminders().remove(orphan);
                }
            }
        }
    }

    @Test
    public void publicReminderCreationCommitsThenRegistersStableAndroidAlarm() {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DateTime future = DateTime.now().plusMinutes(4).withMillisOfSecond(0);
        EventInstance event = new EventInstance(future, EventType.MEDICATION_INTAKE);
        EventReminder created = null;
        try {
            DB.eventInstances().save(event);
            assertNotNull(event.getId());
            assertTrue("The public path must persist and reconcile reminders",
                    Agenda.instance().createReminders(context,
                            Collections.singletonList(event)));

            created = DB.eventReminders().findBy(
                    EventType.MEDICATION_INTAKE, future, null);
            assertNotNull("Committed reminder row must exist in SQLite", created);
            assertNotNull(created.getId());
            assertNotNull("OS PendingIntent must be registered after persistence",
                    PendingIntent.getBroadcast(context, 0,
                            Agenda.reminderBroadcastIntent(context, created),
                            PendingIntentFlags.immutable(PendingIntent.FLAG_NO_CREATE)));
        } finally {
            if (created != null && created.getId() != null) {
                Agenda.instance().cancelAlarm(context, created);
                if (DB.eventReminders().findById(created.getId()) != null) {
                    DB.eventReminders().remove(created);
                }
            }
            if (event.getId() != null && DB.eventInstances().findById(event.getId()) != null) {
                DB.eventInstances().remove(event);
            }
            if (created != null && created.getId() != null) {
                assertNull("Synthetic record must be cleaned after the test",
                        DB.eventReminders().findById(created.getId()));
            }
        }
    }

    private static final class RepeatFixture {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final DateTime due = DateTime.now().plusMinutes(2).withMillisOfSecond(0);
        final EventInstance event = new EventInstance(due, EventType.MEDICATION_INTAKE);
        final EventReminder reminder = new EventReminder(due, EventType.MEDICATION_INTAKE);

        RepeatFixture() {
            reminder.setNextTime(due);
            reminder.setAutoRepeat(true);
            DB.eventInstances().save(event);
            DB.eventReminders().save(reminder);
            assertNotNull(event.getId());
            assertNotNull(reminder.getId());
            Agenda.instance().setAlarm(context, reminder);
        }

        DateTime persistedTime() {
            return DB.eventReminders().findById(reminder.getId()).getNextTime();
        }

        void cleanup() {
            Agenda.instance().cancelAlarm(context, reminder);
            if (DB.eventReminders().findById(reminder.getId()) != null) {
                DB.eventReminders().remove(reminder);
            }
            if (DB.eventInstances().findById(event.getId()) != null) {
                DB.eventInstances().remove(event);
            }
        }
    }

    @Test
    public void repeatCommitsSqlFirstAndRetainsStableAndroidAlarm() {
        assertTrue(DB.initialized);
        RepeatFixture x = new RepeatFixture();
        try {
            DateTime next = x.due.plusMinutes(1);
            assertTrue("Current pending reminder can repeat",
                    Agenda.instance().rescheduleAutoRepeatIfCurrent(x.context, x.reminder, next));
            assertEquals(next, x.persistedTime());
            assertEquals(next, x.reminder.getNextTime());
            assertNotNull("Only committed repeat schedules an OS alarm",
                    PendingIntent.getBroadcast(x.context, 0,
                            Agenda.reminderBroadcastIntent(x.context, x.reminder),
                            PendingIntentFlags.immutable(PendingIntent.FLAG_NO_CREATE)));
            assertFalse("Same alarm delivery must not repeat at the same time again",
                    Agenda.instance().rescheduleAutoRepeatIfCurrent(x.context, x.reminder, next));
        } finally {
            x.cleanup();
        }
    }

    @Test
    public void staleRepeatCannotOverwriteAnIndependentlyDelayedReminder() throws Exception {
        assertTrue(DB.initialized);
        RepeatFixture x = new RepeatFixture();
        try {
            EventReminder newer = DB.eventReminders().findById(x.reminder.getId());
            newer.setNextTime(x.due.plusMinutes(2));
            DB.eventReminders().update(newer);
            assertFalse("An outdated broadcast must not undo the user's newer delay",
                    Agenda.instance().rescheduleAutoRepeatIfCurrent(
                            x.context, x.reminder, x.due.plusMinutes(1)));
            assertEquals(x.due.plusMinutes(2), x.persistedTime());
            assertEquals("Stale callback cannot mutate in-memory reminder",
                    x.due, x.reminder.getNextTime());
            x.event.setCompleted(true);
            DB.eventInstances().update(x.event);
            assertFalse("A completed dose cannot be automatically repeated",
                    Agenda.instance().rescheduleAutoRepeatIfCurrent(
                            x.context, newer, x.due.plusMinutes(3)));
            assertEquals(x.due.plusMinutes(2), x.persistedTime());
        } finally {
            x.cleanup();
        }
    }

    @Test
    public void sqlAbortCannotChangeRepeatTimeOrRetireExistingAlarm() {
        assertTrue(DB.initialized);
        RepeatFixture x = new RepeatFixture();
        SQLiteDatabase sqlite = DB.helper().getWritableDatabase();
        final String trigger = "ci_test_auto_repeat_sql_abort";
        sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
        try {
            sqlite.execSQL("CREATE TRIGGER " + trigger
                    + " BEFORE UPDATE ON EventReminders WHEN OLD._id = " + x.reminder.getId()
                    + " BEGIN SELECT RAISE(ABORT, 'synthetic repeat rollback'); END;");
            boolean rejected = false;
            try {
                Agenda.instance().rescheduleAutoRepeatIfCurrent(
                        x.context, x.reminder, x.due.plusMinutes(1));
            } catch (RuntimeException expected) {
                rejected = true;
            }
            assertTrue("A real SQLite failure must abort automatic repetition", rejected);
            assertEquals(x.due, x.persistedTime());
            assertEquals(x.due, x.reminder.getNextTime());
            assertNotNull("Failed SQL update must preserve original OS alarm",
                    PendingIntent.getBroadcast(x.context, 0,
                            Agenda.reminderBroadcastIntent(x.context, x.reminder),
                            PendingIntentFlags.immutable(PendingIntent.FLAG_NO_CREATE)));
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            assertTrue("Retry must work after the database error is removed",
                    Agenda.instance().rescheduleAutoRepeatIfCurrent(
                            x.context, x.reminder, x.due.plusMinutes(1)));
        } finally {
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            x.cleanup();
        }
    }

}
