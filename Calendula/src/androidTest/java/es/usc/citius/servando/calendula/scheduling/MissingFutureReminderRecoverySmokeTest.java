/*
 * Calendula - An assistant for personal medication management.
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
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;
import es.usc.citius.servando.calendula.util.PendingIntentFlags;

import static org.junit.Assert.*;

/**
 * Disposable clinical fixtures for the otherwise unrecoverable gap:
 * schedule+intake rows COMMIT, process dies before EventReminder is inserted.
 */
@RunWith(AndroidJUnit4.class)
public class MissingFutureReminderRecoverySmokeTest {
    private static final String TRIGGER = "ci_missing_reminder_insert_abort";

    private static PendingIntent token(Context ctx, EventReminder reminder) {
        return PendingIntent.getBroadcast(ctx, 0,
                Agenda.reminderBroadcastIntent(ctx, reminder),
                PendingIntentFlags.immutable(PendingIntent.FLAG_NO_CREATE));
    }

    @Test
    public void startupRebuildsMissingSqliteReminderAndRetriesFailedInsert()
            throws Exception {
        assertTrue(DB.initialized);
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String suffix = Long.toString(System.nanoTime());
        Patient patient = new Patient();
        patient.setCode("ci-reminder-crash-owner-" + suffix);
        patient.setName("Synthetic future reminder owner");
        Medicine medicine = new Medicine("Synthetic future reminder medicine",
                Presentation.PILLS);
        Schedule schedule = new Schedule();
        // The next local midnight is always future and within the existing
        // canBeScheduled() today-inclusive cutoff, even near 23:59.
        DateTime future = DateTime.now().plusDays(1).withTimeAtStartOfDay();
        EventInstance intake = new EventInstance(future, EventType.MEDICATION_INTAKE);
        intake.addParam(EventInstance.PARAM_DOSE, 1.0d);
        // A second, unrelated synthetic patient already has committed SQL
        // reminder data but lost the OS token before a process crash.
        Patient unaffectedOwner = new Patient();
        unaffectedOwner.setCode("ci-already-persisted-owner-" + suffix);
        unaffectedOwner.setName("Synthetic committed reminder owner");
        EventInstance unaffectedDose = new EventInstance(
                future, EventType.MEDICATION_INTAKE);
        unaffectedDose.addParam(EventInstance.PARAM_DOSE, 1.0d);
        EventReminder unaffectedReminder = new EventReminder(
                future, EventType.MEDICATION_INTAKE);
        unaffectedReminder.setNextTime(future);
        SQLiteDatabase sqlite = DB.helper().getWritableDatabase();
        sqlite.execSQL("DROP TRIGGER IF EXISTS " + TRIGGER);
        try {
            assertEquals(1, DB.patients().create(patient));
            medicine.setPatient(patient);
            assertEquals(1, DB.medicines().create(medicine));
            schedule.setPatient(patient);
            schedule.setMedicine(medicine);
            schedule.setDosage(1.0d);
            assertEquals(1, DB.schedules().create(schedule));
            intake.setPatient(patient);
            intake.setRef(schedule.getId());
            assertEquals(1, DB.eventInstances().create(intake));
            assertNull("Committed dose initially has no reminder",
                    DB.eventReminders().findBy(
                            EventType.MEDICATION_INTAKE, future, patient));
            assertEquals(1, DB.patients().create(unaffectedOwner));
            unaffectedDose.setPatient(unaffectedOwner);
            assertEquals(1, DB.eventInstances().create(unaffectedDose));
            unaffectedReminder.setPatient(unaffectedOwner);
            assertEquals(1, DB.eventReminders().create(unaffectedReminder));
            Agenda.instance().cancelAlarm(ctx, unaffectedReminder);
            assertNull("Second patient's committed OS token is missing",
                    token(ctx, unaffectedReminder));

            sqlite.execSQL("CREATE TRIGGER " + TRIGGER
                    + " BEFORE INSERT ON EventReminders WHEN NEW.Patient = "
                    + patient.getId()
                    + " BEGIN SELECT RAISE(ABORT, 'ci simulated process crash'); END;");
            try {
                Agenda.instance().updateAllAlarms(ctx);
                fail("A failed reminder INSERT must leave recovery retryable");
            } catch (RuntimeException expected) {
                // No new alarm may exist when the SQL INSERT rolled back.
            }
            assertNull(DB.eventReminders().findBy(
                    EventType.MEDICATION_INTAKE, future, patient));
            assertNotNull("Dose SQL COMMIT must survive reminder failure",
                    DB.eventInstances().findById(intake.getId()));
            assertNotNull("Failed recovery must still rearm unrelated alarms",
                    token(ctx, unaffectedReminder));
            assertEquals(unaffectedReminder.getId(),
                    DB.eventReminders().findBy(
                            EventType.MEDICATION_INTAKE, future,
                            unaffectedOwner).getId());

            sqlite.execSQL("DROP TRIGGER IF EXISTS " + TRIGGER);
            Agenda.instance().updateAllAlarms(ctx);
            EventReminder restored = DB.eventReminders().findBy(
                    EventType.MEDICATION_INTAKE, future, patient);
            assertNotNull("Startup must reconstruct missing reminder row", restored);
            assertNotNull("Startup must register an OS alarm", token(ctx, restored));

            // Crash after the reminder row COMMIT but before AlarmManager
            // registration. A later startup must rearm the SAME UUID token.
            Agenda.instance().cancelAlarm(ctx, restored);
            assertNull(token(ctx, restored));
            Agenda.instance().updateAllAlarms(ctx);
            assertNotNull("Next restart rearms committed reminder", token(ctx, restored));
            assertEquals("Never duplicate the same patient/time reminder",
                    restored.getId(), DB.eventReminders().findBy(
                            EventType.MEDICATION_INTAKE, future, patient).getId());
            assertNotNull(DB.eventInstances().findById(intake.getId()));
        } finally {
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + TRIGGER);
            if (unaffectedReminder.getId() != null) {
                Agenda.instance().cancelAlarm(ctx, unaffectedReminder);
                if (DB.eventReminders().findById(
                        unaffectedReminder.getId()) != null) {
                    DB.eventReminders().remove(unaffectedReminder);
                }
            }
            if (unaffectedDose.getId() != null
                    && DB.eventInstances().findById(
                            unaffectedDose.getId()) != null) {
                DB.eventInstances().remove(unaffectedDose);
            }
            if (unaffectedOwner.getId() != null
                    && DB.patients().findById(unaffectedOwner.getId()) != null) {
                DB.patients().remove(unaffectedOwner);
            }
            EventReminder reminder = DB.eventReminders().findBy(
                    EventType.MEDICATION_INTAKE, future, patient);
            if (reminder != null) {
                Agenda.instance().cancelAlarm(ctx, reminder);
                DB.eventReminders().remove(reminder);
            }
            if (intake.getId() != null
                    && DB.eventInstances().findById(intake.getId()) != null) {
                DB.eventInstances().remove(intake);
            }
            if (schedule.getId() != null
                    && DB.schedules().findById(schedule.getId()) != null) {
                DB.schedules().remove(schedule);
            }
            if (medicine.getId() != null
                    && DB.medicines().findById(medicine.getId()) != null) {
                DB.medicines().remove(medicine);
            }
            if (patient.getId() != null
                    && DB.patients().findById(patient.getId()) != null) {
                DB.patients().remove(patient);
            }
        }
    }

    @Test
    public void blockedScheduleMustNotAcquireReminderDuringStartupRecovery()
            throws Exception {
        assertTrue(DB.initialized);
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String suffix = Long.toString(System.nanoTime());
        Patient owner = new Patient();
        owner.setCode("ci-recovery-blocked-owner-" + suffix);
        owner.setName("Synthetic blocked reminder owner");
        Medicine medicine = new Medicine("Synthetic blocked reminder medicine",
                Presentation.PILLS);
        Schedule schedule = new Schedule();
        EventInstance intake = new EventInstance(
                DateTime.now().plusDays(1).withTimeAtStartOfDay(),
                EventType.MEDICATION_INTAKE);
        intake.addParam(EventInstance.PARAM_DOSE, 1.0d);
        try {
            assertEquals(1, DB.patients().create(owner));
            medicine.setPatient(owner);
            assertEquals(1, DB.medicines().create(medicine));
            schedule.setPatient(owner);
            schedule.setMedicine(medicine);
            schedule.addState(Schedule.ScheduleState.BLOCKED);
            schedule.setDosage(1.0d);
            assertEquals(1, DB.schedules().create(schedule));
            intake.setRef(schedule.getId());
            intake.setPatient(owner);
            assertEquals(1, DB.eventInstances().create(intake));
            Agenda.instance().updateAllAlarms(ctx);
            assertNull("A blocked medication must never be rearmed",
                    DB.eventReminders().findBy(
                            EventType.MEDICATION_INTAKE, intake.getTime(), owner));

            // Even without BLOCKED, a pending official-treatment review
            // still forbids automatically generating clinical reminders.
            Schedule stored = DB.schedules().findById(schedule.getId());
            stored.removeState(Schedule.ScheduleState.BLOCKED);
            stored.addState(Schedule.ScheduleState.PENDING_REVIEW_AFTER_UPDATE);
            assertEquals(1, DB.schedules().update(stored));
            Agenda.instance().updateAllAlarms(ctx);
            assertNull("Unreviewed official treatment must stay unscheduled",
                    DB.eventReminders().findBy(
                            EventType.MEDICATION_INTAKE, intake.getTime(), owner));
            assertNotNull("Pending dose snapshot retained, never auto-confirmed",
                    DB.eventInstances().findById(intake.getId()));
        } finally {
            EventReminder reminder = DB.eventReminders().findBy(
                    EventType.MEDICATION_INTAKE, intake.getTime(), owner);
            if (reminder != null) {
                Agenda.instance().cancelAlarm(ctx, reminder);
                DB.eventReminders().remove(reminder);
            }
            if (intake.getId() != null
                    && DB.eventInstances().findById(intake.getId()) != null) {
                DB.eventInstances().remove(intake);
            }
            if (schedule.getId() != null
                    && DB.schedules().findById(schedule.getId()) != null) {
                DB.schedules().remove(schedule);
            }
            if (medicine.getId() != null
                    && DB.medicines().findById(medicine.getId()) != null) {
                DB.medicines().remove(medicine);
            }
            if (owner.getId() != null
                    && DB.patients().findById(owner.getId()) != null) {
                DB.patients().remove(owner);
            }
        }
    }
}
