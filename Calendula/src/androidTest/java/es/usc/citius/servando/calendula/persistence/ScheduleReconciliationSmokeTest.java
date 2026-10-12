/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.persistence;

import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;

import es.usc.citius.servando.calendula.scheduling.model.EventReminder;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Real SQLite failure injection; synthetic references and past timestamps only. */
@RunWith(AndroidJUnit4.class)
public class ScheduleReconciliationSmokeTest {
    @Test
    public void newlyCreatedScheduleCannotClaimSuccessWhenReminderWriteFails() {
        assertTrue(DB.initialized);
        DateTime when = DateTime.now().plusMinutes(2).withMillisOfSecond(0);
        Long ref = -System.nanoTime();
        EventInstance pending = event(ref, when, 1.0d);
        SQLiteDatabase sqlite = DB.helper().getWritableDatabase();
        final String trigger = "ci_test_new_schedule_reminder_abort";
        sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
        try {
            sqlite.execSQL("CREATE TRIGGER " + trigger
                    + " BEFORE INSERT ON EventReminders"
                    + " BEGIN SELECT RAISE(ABORT, 'synthetic reminder insertion failure'); END;");
            boolean rejected = false;
            try {
                ScheduleUtils.instance().persistAndScheduleNewOccurrences(
                        InstrumentationRegistry.getInstrumentation().getTargetContext(),
                        Collections.singletonList(pending));
            } catch (RuntimeException expected) {
                rejected = true;
            }
            assertTrue("Failure must be reported, never disguised as saved", rejected);
            assertNotNull("Committed occurrence remains available for recovery",
                    pending.getId());
            assertNotNull(DB.eventInstances().findById(pending.getId()));
            assertNull("SQL failure must not leave half-created reminder",
                    DB.eventReminders().findBy(EventType.MEDICATION_INTAKE,
                            when, null));
        } finally {
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            EventReminder reminder = DB.eventReminders().findBy(
                    EventType.MEDICATION_INTAKE, when, null);
            // The injected BEFORE INSERT abort means no alarm could have
            // been registered. Defensive SQLite cleanup uses public DAO APIs.
            if (reminder != null) {
                DB.eventReminders().remove(reminder);
            }
            if (pending.getId() != null && DB.eventInstances().findById(pending.getId()) != null) {
                DB.eventInstances().remove(pending);
            }
        }
    }

    @Test
    public void failedInitialDoseInsertRollsBackNewScheduleRow() throws Exception {
        assertTrue(DB.initialized);
        String key = Long.toString(System.nanoTime());
        Patient owner = new Patient();
        owner.setCode("ci-atomic-schedule-owner-" + key);
        owner.setName("Synthetic new schedule owner");
        Medicine medicine = new Medicine("Synthetic initial schedule medicine", Presentation.PILLS);
        Schedule schedule = new Schedule(medicine);
        DateTime when = DateTime.now().plusDays(3).withMillisOfSecond(0);
        EventInstance pending = new EventInstance(when, EventType.MEDICATION_INTAKE);
        pending.addParam(EventInstance.PARAM_DOSE, 1.0d);
        SQLiteDatabase sqlite = DB.helper().getWritableDatabase();
        final String trigger = "ci_new_schedule_event_abort";
        sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
        try {
            assertEquals(1, DB.patients().create(owner));
            medicine.setPatient(owner);
            assertEquals(1, DB.medicines().create(medicine));
            schedule.setPatient(owner);
            sqlite.execSQL("CREATE TRIGGER " + trigger
                    + " BEFORE INSERT ON EventInstances"
                    + " BEGIN SELECT RAISE(ABORT, 'synthetic event insert failure'); END;");
            boolean rejected = false;
            try {
                ScheduleUtils.instance().persistNewScheduleRows(
                        InstrumentationRegistry.getInstrumentation().getTargetContext(),
                        schedule, java.util.Collections.singletonList(pending));
            } catch (RuntimeException expected) {
                rejected = true;
            }
            assertTrue("A failed dose insert must fail prescription creation", rejected);
            assertNotNull("The attempted insert obtained an identifier", schedule.getId());
            assertNull("New prescription must be rolled back completely",
                    DB.schedules().findById(schedule.getId()));
            assertFalse("The failed intake must not be persisted",
                    DB.eventInstances().exists(EventType.MEDICATION_INTAKE,
                            when, schedule.getId()));
            assertNotNull("The patient must not be deleted by rollback",
                    DB.patients().findById(owner.getId()));
            assertNotNull("The medicine must not be deleted by rollback",
                    DB.medicines().findById(medicine.getId()));
        } finally {
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            if (pending.getId() != null && DB.eventInstances().findById(pending.getId()) != null) {
                DB.eventInstances().remove(pending);
            }
            if (schedule.getId() != null && DB.schedules().findById(schedule.getId()) != null) {
                DB.schedules().remove(schedule);
            }
            if (medicine.getId() != null && DB.medicines().findById(medicine.getId()) != null) {
                DB.medicines().remove(medicine);
            }
            if (owner.getId() != null && DB.patients().findById(owner.getId()) != null) {
                DB.patients().remove(owner);
            }
        }
    }

    @Test
    public void failedEditedOccurrenceDeleteRollsBackStoredPrescriptionState()
            throws Exception {
        assertTrue(DB.initialized);
        String key = Long.toString(System.nanoTime());
        Patient patient = new Patient();
        patient.setName("Synthetic edited schedule owner");
        patient.setCode("ci-edit-schedule-rollback-" + key);
        Medicine medicine = new Medicine("Synthetic edited schedule medicine",
                Presentation.PILLS);
        Schedule schedule = new Schedule(medicine);
        DateTime from = DateTime.now().minusDays(4);
        DateTime obsoleteTime = from.plusDays(1).withMillisOfSecond(0);
        EventInstance obsolete = event(null, obsoleteTime, 1.0d);
        SQLiteDatabase sqlite = DB.helper().getWritableDatabase();
        final String trigger = "ci_edit_schedule_occurrence_abort";
        sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
        try {
            assertEquals(1, DB.patients().create(patient));
            medicine.setPatient(patient);
            assertEquals(1, DB.medicines().create(medicine));
            schedule.setPatient(patient);
            assertEquals(1, DB.schedules().create(schedule));
            obsolete.setPatient(patient);
            obsolete.setRef(schedule.getId());
            assertEquals(1, DB.eventInstances().create(obsolete));
            assertFalse(DB.schedules().findById(schedule.getId())
                    .hasState(Schedule.ScheduleState.BLOCKED));

            sqlite.execSQL("CREATE TRIGGER " + trigger
                    + " BEFORE DELETE ON EventInstances WHEN OLD._id = " + obsolete.getId()
                    + " BEGIN SELECT RAISE(ABORT, 'synthetic recurrence delete failure'); END;");
            schedule.addState(Schedule.ScheduleState.BLOCKED);
            try {
                ScheduleUtils.instance().persistEditedScheduleRows(
                        InstrumentationRegistry.getInstrumentation().getTargetContext(),
                        schedule, from, java.util.Collections.emptyList());
                fail("Rejected occurrence deletion must reject prescription edit");
            } catch (RuntimeException expected) {
                // Both the model UPDATE and event DELETE must be rolled back.
            }
            Schedule fromSqlite = DB.schedules().findById(schedule.getId());
            assertNotNull(fromSqlite);
            assertEquals(1, DB.schedules().refresh(fromSqlite));
            assertFalse("Uncommitted blocked state must not survive rollback",
                    fromSqlite.hasState(Schedule.ScheduleState.BLOCKED));
            assertNotNull("Previous pending intake must survive the failed edit",
                    DB.eventInstances().findById(obsolete.getId()));
            assertEquals(1.0d, DB.eventInstances().findById(obsolete.getId())
                    .getDoubleParam(EventInstance.PARAM_DOSE), 0.0001d);
        } finally {
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            if (obsolete.getId() != null && DB.eventInstances().findById(obsolete.getId()) != null) {
                DB.eventInstances().remove(obsolete);
            }
            if (schedule.getId() != null && DB.schedules().findById(schedule.getId()) != null) {
                DB.schedules().remove(schedule);
            }
            if (medicine.getId() != null && DB.medicines().findById(medicine.getId()) != null) {
                DB.medicines().remove(medicine);
            }
            if (patient.getId() != null && DB.patients().findById(patient.getId()) != null) {
                DB.patients().remove(patient);
            }
        }
    }

    @Test
    public void failedObsoleteDeleteRollsBackEarlierDoseUpdateAndNewOccurrences() {
        assertTrue(DB.initialized);
        Schedule schedule = new Schedule();
        schedule.setId(-System.nanoTime());
        DateTime from = DateTime.now().minusDays(6);
        DateTime existingTime = from.plusDays(1).withMillisOfSecond(0);
        DateTime obsoleteTime = from.plusDays(2).withMillisOfSecond(0);
        DateTime newTime = from.plusDays(3).withMillisOfSecond(0);
        EventInstance existing = event(schedule.getId(), existingTime, 1.0d);
        EventInstance obsolete = event(schedule.getId(), obsoleteTime, 2.0d);
        EventInstance revised = event(schedule.getId(), existingTime, 5.0d);
        EventInstance added = event(schedule.getId(), newTime, 3.0d);
        SQLiteDatabase sqlite = DB.helper().getWritableDatabase();
        final String trigger = "ci_test_recurrence_delete_abort";
        sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
        try {
            DB.eventInstances().save(existing);
            DB.eventInstances().save(obsolete);
            assertNotNull(existing.getId());
            assertNotNull(obsolete.getId());
            sqlite.execSQL("CREATE TRIGGER " + trigger
                    + " BEFORE DELETE ON EventInstances WHEN OLD._id = " + obsolete.getId()
                    + " BEGIN SELECT RAISE(ABORT, 'synthetic recurrence failure'); END;");
            try {
                ScheduleUtils.instance().reconcileScheduleOccurrences(
                        InstrumentationRegistry.getInstrumentation().getTargetContext(),
                        schedule, from, Arrays.asList(revised, added));
                fail("Failed pruning must roll back earlier recurrence changes");
            } catch (RuntimeException expected) {
                // The previous event update must not survive this later failure.
            }
            assertEquals("Original pending dose must survive rollback",
                    1.0d, DB.eventInstances().findById(existing.getId())
                            .getDoubleParam(EventInstance.PARAM_DOSE), 0.0001d);
            assertNotNull("Obsolete pending row must survive rollback",
                    DB.eventInstances().findById(obsolete.getId()));
            assertFalse("New time must not have been inserted before SQL COMMIT",
                    DB.eventInstances().exists(EventType.MEDICATION_INTAKE,
                            newTime, schedule.getId()));

            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            ScheduleUtils.instance().reconcileScheduleOccurrences(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(),
                    schedule, from, Arrays.asList(revised, added));
            assertEquals("Retry updates original pending dose",
                    5.0d, DB.eventInstances().findById(existing.getId())
                            .getDoubleParam(EventInstance.PARAM_DOSE), 0.0001d);
            assertNull("Retry removes the obsolete pending row",
                    DB.eventInstances().findById(obsolete.getId()));
            assertTrue("Retry adds the new occurrence once",
                    DB.eventInstances().exists(EventType.MEDICATION_INTAKE,
                            newTime, schedule.getId()));
        } finally {
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            for (EventInstance row : DB.eventInstances().findByTypeAndTime(
                    EventType.MEDICATION_INTAKE, newTime)) {
                if (schedule.getId().equals(row.getRef())) {
                    DB.eventInstances().remove(row);
                }
            }
            for (EventInstance row : new EventInstance[]{existing, obsolete}) {
                if (row.getId() != null && DB.eventInstances().findById(row.getId()) != null) {
                    DB.eventInstances().remove(row);
                }
            }
        }
    }

    private static EventInstance event(Long scheduleId, DateTime time, double dose) {
        EventInstance event = new EventInstance(time, EventType.MEDICATION_INTAKE);
        event.setRef(scheduleId);
        event.addParam(EventInstance.PARAM_DOSE, dose);
        return event;
    }
}
