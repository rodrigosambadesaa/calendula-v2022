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
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;
import es.usc.citius.servando.calendula.util.PendingIntentFlags;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Uses only synthetic SQLite fixture rows and their AlarmManager tokens. */
@RunWith(AndroidJUnit4.class)
public class AtomicMedicationDecisionSmokeTest {
    private static PendingIntent token(Context context, EventReminder reminder) {
        return PendingIntent.getBroadcast(context, 0,
                Agenda.reminderBroadcastIntent(context, reminder),
                PendingIntentFlags.immutable(PendingIntent.FLAG_NO_CREATE));
    }

    @Test
    public void confirmingAssignedIntakeCommitsEventStockAndRemovesAlarm() {
        exerciseDecision(true, true);
    }

    @Test
    public void unassignedIntakeCannotBeConfirmedWithoutVerifiedStockOwnership() {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DateTime time = DateTime.now().plusHours(12).withMillisOfSecond(0);
        EventInstance orphan = new EventInstance(time, EventType.MEDICATION_INTAKE);
        EventReminder reminder = new EventReminder(time, EventType.MEDICATION_INTAKE);
        reminder.setNextTime(time);
        try {
            DB.eventInstances().save(orphan);
            DB.eventReminders().save(reminder);
            Agenda.instance().setAlarm(context, reminder);
            assertNotNull(token(context, reminder));
            boolean rejected = false;
            try {
                Agenda.instance().confirmReminder(context, reminder.getId());
            } catch (RuntimeException expected) {
                rejected = true;
            }
            assertTrue("Orphan intake cannot be silently marked taken", rejected);
            assertFalse(DB.eventInstances().findById(orphan.getId()).completed());
            assertNotNull(DB.eventReminders().findById(reminder.getId()));
            assertNotNull(token(context, reminder));
        } finally {
            if (reminder.getId() != null) {
                Agenda.instance().cancelAlarm(context, reminder);
                if (DB.eventReminders().findById(reminder.getId()) != null) {
                    DB.eventReminders().remove(reminder);
                }
            }
            if (orphan.getId() != null && DB.eventInstances().findById(orphan.getId()) != null) {
                DB.eventInstances().remove(orphan);
            }
        }
    }

    @Test
    public void cancellingAssignedIntakeCommitsEventAndRemovesAlarm() {
        exerciseDecision(false, true);
    }

    private void exerciseDecision(boolean confirm, boolean withPatient) {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DateTime time = DateTime.now().plusHours(confirm ? 15 : 16).withMillisOfSecond(0);
        Patient synthetic = null;
        Medicine medicine = null;
        Schedule schedule = null;
        EventInstance event = new EventInstance(time, EventType.MEDICATION_INTAKE);
        EventReminder reminder = new EventReminder(time, EventType.MEDICATION_INTAKE);
        reminder.setNextTime(time);
        try {
            if (withPatient) {
                synthetic = new Patient();
                synthetic.setCode("ci-decision-" + System.nanoTime());
                synthetic.setName("Synthetic medication test patient");
                DB.patients().save(synthetic);
                event.setPatient(synthetic);
                reminder.setPatient(synthetic);
            }
            if (confirm) {
                medicine = new Medicine("Synthetic confirmed decision medicine", Presentation.PILLS);
                medicine.setPatient(synthetic);
                medicine.setStock(10.0f);
                DB.medicines().create(medicine);
                schedule = new Schedule(medicine);
                schedule.setPatient(synthetic);
                DB.schedules().create(schedule);
                event.setRef(schedule.getId());
                event.addParam(EventInstance.PARAM_DOSE, 2.0d);
            }
            DB.eventInstances().save(event);
            DB.eventReminders().save(reminder);
            assertNotNull(event.getId());
            assertNotNull(reminder.getId());
            Agenda.instance().setAlarm(context, reminder);
            assertNotNull("A synthetic alarm must exist before the decision",
                    token(context, reminder));

            if (confirm) {
                Agenda.instance().confirmReminder(context, reminder.getId());
            } else {
                Agenda.instance().cancelReminder(context, reminder.getId());
            }

            EventInstance updated = DB.eventInstances().findById(event.getId());
            assertNotNull("The original event must be preserved", updated);
            if (confirm) {
                assertTrue("The confirmed intake must be completed", updated.completed());
                assertFalse("Confirming an intake must not mark it cancelled", updated.cancelled());
                assertNotNull(medicine);
                assertTrue("Direct confirmation must deduct inventory exactly once",
                        Math.abs(DB.medicines().findById(medicine.getId()).getStock() - 8.0f) < 0.001f);
            } else {
                assertTrue("Cancelling an intake must mark it cancelled", updated.cancelled());
                assertFalse("Cancelling must not mark it completed", updated.completed());
            }
            assertNull("The reminder must be deleted from SQLite",
                    DB.eventReminders().findById(reminder.getId()));
            assertNull("The matching Android alarm token must be retired",
                    token(context, reminder));
        } finally {
            if (reminder.getId() != null) {
                Agenda.instance().cancelAlarm(context, reminder);
                if (DB.eventReminders().findById(reminder.getId()) != null) {
                    DB.eventReminders().remove(reminder);
                }
            }
            if (event.getId() != null && DB.eventInstances().findById(event.getId()) != null) {
                DB.eventInstances().remove(event);
            }
            if (schedule != null && schedule.getId() != null) {
                DB.schedules().remove(schedule);
            }
            if (medicine != null && medicine.getId() != null) {
                DB.medicines().remove(medicine);
            }
            if (synthetic != null && synthetic.getId() != null) {
                DB.patients().remove(synthetic);
            }
        }
    }

    @Test
    public void failedDeleteRollsBackConfirmedIntakeAndPreservesAlarm() {
        exerciseFailedDecision(true, false);
    }

    @Test
    public void failedDeleteRollsBackCancelledIntakeAndPreservesAlarm() {
        exerciseFailedDecision(false, false);
    }

    /**
     * Inject a real SQLite constraint-style failure after changing the event,
     * then verify that the entire ORMLite transaction rolls back on-device.
     * The trigger is scoped to the synthetic row ID and removed in finally.
     */
    @Test
    public void ignoredDeleteRollsBackConfirmedIntakeAndPreservesAlarm() {
        exerciseFailedDecision(true, true);
    }

    @Test
    public void ignoredDeleteRollsBackCancelledIntakeAndPreservesAlarm() {
        exerciseFailedDecision(false, true);
    }

    private void exerciseFailedDecision(boolean confirm, boolean silentZeroRowDelete) {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DateTime time = DateTime.now().plusHours(confirm ? 19 : 20).withMillisOfSecond(0);
        EventInstance event = new EventInstance(time, EventType.MEDICATION_INTAKE);
        EventReminder reminder = new EventReminder(time, EventType.MEDICATION_INTAKE);
        reminder.setNextTime(time);
        Patient synthetic = null;
        Medicine medicine = null;
        Schedule schedule = null;
        SQLiteDatabase sqlite = DB.helper().getWritableDatabase();
        final String trigger = "ci_test_block_medication_reminder_delete";
        sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
        try {
            if (confirm) {
                synthetic = new Patient();
                synthetic.setCode("ci-decision-failure-" + System.nanoTime());
                synthetic.setName("Synthetic confirmed failure patient");
                DB.patients().save(synthetic);
                event.setPatient(synthetic);
                reminder.setPatient(synthetic);
                medicine = new Medicine("Synthetic failure stock medicine", Presentation.PILLS);
                medicine.setPatient(synthetic);
                medicine.setStock(10.0f);
                DB.medicines().create(medicine);
                schedule = new Schedule(medicine);
                schedule.setPatient(synthetic);
                DB.schedules().create(schedule);
                event.setRef(schedule.getId());
                event.addParam(EventInstance.PARAM_DOSE, 2.0d);
            }
            DB.eventInstances().save(event);
            DB.eventReminders().save(reminder);
            assertNotNull(event.getId());
            assertNotNull(reminder.getId());
            Agenda.instance().setAlarm(context, reminder);
            assertNotNull(token(context, reminder));

            sqlite.execSQL("CREATE TRIGGER " + trigger
                    + " BEFORE DELETE ON EventReminders"
                    + " WHEN OLD._id = " + reminder.getId()
                    + (silentZeroRowDelete
                        ? " BEGIN SELECT RAISE(IGNORE); END;"
                        : " BEGIN SELECT RAISE(ABORT, 'synthetic SQLite delete failure'); END;"));
            try {
                if (confirm) {
                    Agenda.instance().confirmReminder(context, reminder.getId());
                } else {
                    Agenda.instance().cancelReminder(context, reminder.getId());
                }
                org.junit.Assert.fail("The injected SQL failure or zero-row delete must abort the decision");
            } catch (RuntimeException expected) {
                // A SQL failure must propagate; never declare a completed intake.
            }

            EventInstance after = DB.eventInstances().findById(event.getId());
            assertNotNull(after);
            assertFalse("Failed SQL commit must roll back completion", after.completed());
            assertFalse("Failed SQL commit must roll back cancellation", after.cancelled());
            if (confirm) {
                assertNotNull(medicine);
                assertTrue("The stock deduction must also roll back with reminder deletion",
                        Math.abs(DB.medicines().findById(medicine.getId()).getStock() - 10.0f) < 0.001f);
            }
            assertNotNull("Failed SQL commit must preserve the persisted reminder",
                    DB.eventReminders().findById(reminder.getId()));
            assertNotNull("Failed SQL commit must retain the Android alarm token",
                    token(context, reminder));
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
            if (schedule != null && schedule.getId() != null) {
                DB.schedules().remove(schedule);
            }
            if (medicine != null && medicine.getId() != null) {
                DB.medicines().remove(medicine);
            }
            if (synthetic != null && synthetic.getId() != null) {
                DB.patients().remove(synthetic);
            }
        }
    }
}
