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

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;
import es.usc.citius.servando.calendula.util.PendingIntentFlags;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * On-device SQLite and AlarmManager verification with synthetic patient data.
 * A cancelled but uncompleted intake cannot keep a stale medical alarm alive.
 */
@RunWith(AndroidJUnit4.class)
public class CancelledIntakeAlarmCleanupSmokeTest {

    private static PendingIntent alarm(Context context, EventReminder reminder) {
        return PendingIntent.getBroadcast(context, 0,
                Agenda.reminderBroadcastIntent(context, reminder),
                PendingIntentFlags.immutable(PendingIntent.FLAG_NO_CREATE));
    }

    @Test
    public void cleanupDeletesCancelledIntakeReminderWithoutChangingEvent() {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DateTime time = DateTime.now().plusHours(21).withMillisOfSecond(0);
        Patient synthetic = new Patient();
        synthetic.setCode("ci-cancelled-alarm-" + System.nanoTime());
        synthetic.setName("Synthetic cancelled medication test");
        EventInstance cancelled = new EventInstance(time, EventType.MEDICATION_INTAKE);
        cancelled.setPatient(synthetic);
        cancelled.setCancelled(true);
        EventReminder reminder = new EventReminder(time, EventType.MEDICATION_INTAKE);
        reminder.setPatient(synthetic);
        reminder.setNextTime(time);
        try {
            DB.patients().save(synthetic);
            assertNotNull(synthetic.getId());
            DB.eventInstances().save(cancelled);
            DB.eventReminders().save(reminder);
            assertNotNull(cancelled.getId());
            assertNotNull(reminder.getId());
            assertFalse("Cancelled intakes cannot authorize medication alarms",
                    DB.eventInstances().existsPending(
                            EventType.MEDICATION_INTAKE, time, synthetic));

            Agenda.instance().setAlarm(context, reminder);
            assertNotNull("Synthetic alarm must initially be registered",
                    alarm(context, reminder));
            Agenda.instance().cleanReminderIfPossible(
                    context, synthetic, EventType.MEDICATION_INTAKE, time);

            assertNull("Cancelled intake must not retain an orphan SQL reminder",
                    DB.eventReminders().findById(reminder.getId()));
            assertNull("Cancelled intake must not retain an Android alarm",
                    alarm(context, reminder));
            EventInstance preserved = DB.eventInstances().findById(cancelled.getId());
            assertNotNull("Cleanup must not erase the recorded cancellation", preserved);
            assertTrue("Cancelled event state must be preserved", preserved.cancelled());
        } finally {
            if (reminder.getId() != null) {
                Agenda.instance().cancelAlarm(context, reminder);
                if (DB.eventReminders().findById(reminder.getId()) != null) {
                    DB.eventReminders().remove(reminder);
                }
            }
            if (cancelled.getId() != null && DB.eventInstances().findById(cancelled.getId()) != null) {
                DB.eventInstances().remove(cancelled);
            }
            if (synthetic.getId() != null) {
                DB.patients().remove(synthetic);
            }
        }
    }

    @Test
    public void ignoredSqliteDeleteMustPreserveActualAndroidAlarm() {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DateTime time = DateTime.now().plusHours(22).withMillisOfSecond(0);
        EventReminder reminder = new EventReminder(time, EventType.MEDICATION_INTAKE);
        reminder.setNextTime(time);
        SQLiteDatabase db = DB.helper().getWritableDatabase();
        final String trigger = "ci_test_ignore_cleanup_delete";
        db.execSQL("DROP TRIGGER IF EXISTS " + trigger);
        try {
            DB.eventReminders().save(reminder);
            assertNotNull(reminder.getId());
            Agenda.instance().setAlarm(context, reminder);
            assertNotNull(alarm(context, reminder));

            // RAISE(IGNORE) returns zero deleted rows without an exception.
            // A real SQLite row must remain, and its Android token must survive.
            db.execSQL("CREATE TRIGGER " + trigger
                    + " BEFORE DELETE ON EventReminders"
                    + " WHEN OLD._id = " + reminder.getId()
                    + " BEGIN SELECT RAISE(IGNORE); END;");
            try {
                Agenda.instance().cleanReminderIfPossible(
                        context, null, EventType.MEDICATION_INTAKE, time);
                org.junit.Assert.fail("A silent zero-row delete must not succeed");
            } catch (IllegalStateException expected) {
                // Verified surviving SQLite row; no platform cancellation allowed.
            }
            assertNotNull("Synthetic reminder row must still exist",
                    DB.eventReminders().findById(reminder.getId()));
            assertNotNull("Persisted reminder must keep its real PendingIntent",
                    alarm(context, reminder));
        } finally {
            db.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            if (reminder.getId() != null) {
                Agenda.instance().cancelAlarm(context, reminder);
                if (DB.eventReminders().findById(reminder.getId()) != null) {
                    DB.eventReminders().remove(reminder);
                }
            }
        }
    }
}
