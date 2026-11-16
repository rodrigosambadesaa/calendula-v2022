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

import org.dmfs.rfc5545.recur.Freq;
import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

import es.usc.citius.servando.calendula.allergies.AllergenType;
import es.usc.citius.servando.calendula.allergies.AllergenVO;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.PatientAlert;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;
import es.usc.citius.servando.calendula.scheduling.model.recur.RecurringEvent;
import es.usc.citius.servando.calendula.util.PendingIntentFlags;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;
import es.usc.citius.servando.calendula.util.alerts.AtomicMedicineAllergyEditor;

import static org.junit.Assert.*;

/**
 * Disposable future-dose fixture: a newly committed HIGH allergy warning
 * must retire the prior platform alarm and persisted reminder, not just the
 * SQLite occurrence. No real patients, prescriptions or providers are used.
 */
@RunWith(AndroidJUnit4.class)
public class AtomicAllergyReminderCleanupSmokeTest {
    @Test
    public void addingHighAllergyWarningRetiresOldDosePendingIntentAfterCommit()
            throws Exception {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Patient previous = DB.patients().getActive(context);
        String seed = Long.toString(System.nanoTime());
        Patient owner = new Patient();
        owner.setCode("ci-allergy-os-owner-" + seed);
        owner.setName("Synthetic reminder cleanup owner");
        Medicine medicine = new Medicine("Synthetic scheduled medicine", Presentation.PILLS);
        Schedule schedule = null;
        EventInstance intake = null;
        EventReminder reminder = null;
        try {
            assertEquals(1, DB.patients().create(owner));
            DB.patients().setActive(owner);
            medicine.setPatient(owner);
            assertEquals(1, DB.medicines().create(medicine));

            RecurringEvent recurrence = new RecurringEvent.Builder()
                    .from(DateTime.now().minusDays(1))
                    .repeatEvery(12, Freq.HOURLY).build();
            schedule = new Schedule(recurrence);
            schedule.setPatient(owner);
            schedule.setMedicine(medicine);
            schedule.setDosage(1.0d);
            assertEquals(1, DB.schedules().create(schedule));

            DateTime due = DateTime.now().plusHours(3);
            intake = new EventInstance(due, EventType.MEDICATION_INTAKE);
            intake.setPatient(owner);
            intake.setRef(schedule.getId());
            intake.addParam(EventInstance.PARAM_DOSE, 1.0d);
            assertEquals(1, DB.eventInstances().create(intake));

            reminder = new EventReminder(due, EventType.MEDICATION_INTAKE);
            reminder.setPatient(owner);
            reminder.setNextTime(due.minusMinutes(10));
            assertEquals(1, DB.eventReminders().create(reminder));
            Agenda.instance().setAlarm(context, reminder);
            PendingIntent oldToken = findToken(context, reminder);
            assertNotNull("Fixture must schedule an actual OS PendingIntent", oldToken);

            // Force SQL to fail *after* the medicine and HIGH warning were
            // written and the schedule-blocking transaction began deleting
            // future doses. Neither SQL state nor the Android token may be
            // retired when the outer transaction rolls back.
            SQLiteDatabase sqlite = DB.helper().getWritableDatabase();
            String trigger = "ci_allergy_reminder_rollback_abort";
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            try {
                sqlite.execSQL("CREATE TRIGGER " + trigger
                        + " BEFORE DELETE ON EventInstances WHEN OLD._id = "
                        + intake.getId()
                        + " BEGIN SELECT RAISE(ABORT, 'synthetic allergy rollback'); END;");
                boolean rejected = false;
                try {
                    AtomicMedicineAllergyEditor.edit(context, medicine,
                            Collections.singletonList(new AllergenVO(
                                    AllergenType.EXCIPIENT, "Synthetic excipient",
                                    "ci-synthetic-" + seed)));
                } catch (RuntimeException expected) {
                    rejected = true;
                }
                assertTrue("Injected SQL failure must abort the edit", rejected);
                assertFalse("Rolled-back edit must leave schedule active",
                        DB.schedules().findById(schedule.getId())
                                .hasState(Schedule.ScheduleState.BLOCKED));
                assertTrue("Rolled-back HIGH warning must not persist",
                        DB.alerts().findByMedicineAndLevel(
                                medicine, PatientAlert.Level.HIGH).isEmpty());
                assertNotNull("Rolled-back future dose must survive",
                        DB.eventInstances().findById(intake.getId()));
                assertNotNull("Rolled-back reminder must survive",
                        DB.eventReminders().findById(reminder.getId()));
                assertNotNull("Platform alarm must not be cancelled before COMMIT",
                        findToken(context, reminder));
            } finally {
                sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            }

            // Retry the same medicine edit with no injected SQL error:
            // the committed HIGH warning now retires SQL and OS reminders.
            AtomicMedicineAllergyEditor.edit(context, medicine,
                    Collections.singletonList(new AllergenVO(
                            AllergenType.EXCIPIENT, "Synthetic excipient",
                            "ci-synthetic-" + seed)));

            assertTrue("A committed HIGH warning blocks future medication",
                    DB.schedules().findById(schedule.getId())
                            .hasState(Schedule.ScheduleState.BLOCKED));
            assertFalse("HIGH warning must be committed",
                    DB.alerts().findByMedicineAndLevel(
                            medicine, PatientAlert.Level.HIGH).isEmpty());
            assertNull("Future dose occurrence is no longer actionable",
                    DB.eventInstances().findById(intake.getId()));
            assertNull("Stale reminder row must be retired after COMMIT",
                    DB.eventReminders().findById(reminder.getId()));
            assertNull("Stale Android token must be cancelled after COMMIT",
                    findToken(context, reminder));
        } finally {
            if (reminder != null && reminder.getId() != null) {
                Agenda.instance().cancelAlarm(context, reminder);
                if (DB.eventReminders().findById(reminder.getId()) != null) {
                    DB.eventReminders().deleteById(reminder.getId());
                }
            }
            if (medicine.getId() != null) {
                for (PatientAlert warning : DB.alerts().findBy(
                        PatientAlert.COLUMN_MEDICINE, medicine.getId())) {
                    DB.alerts().deleteById(warning.id());
                }
            }
            if (intake != null && intake.getId() != null
                    && DB.eventInstances().findById(intake.getId()) != null) {
                DB.eventInstances().deleteById(intake.getId());
            }
            if (schedule != null && schedule.getId() != null
                    && DB.schedules().findById(schedule.getId()) != null) {
                DB.eventInstances().removeByRef(schedule.getId());
                DB.schedules().deleteById(schedule.getId());
            }
            if (medicine.getId() != null
                    && DB.medicines().findById(medicine.getId()) != null) {
                DB.medicines().deleteById(medicine.getId());
            }
            if (previous != null && previous.getId() != null
                    && DB.patients().findById(previous.getId()) != null) {
                DB.patients().setActive(previous);
            } else {
                PreferenceUtils.edit()
                        .remove(PreferenceKeys.PATIENTS_ACTIVE.key())
                        .remove(PreferenceKeys.PATIENTS_ACTIVE_OWNER.key())
                        .remove(PreferenceKeys.PATIENTS_RESELECTION_REQUIRED.key())
                        .commit();
            }
            if (owner.getId() != null && DB.patients().findById(owner.getId()) != null) {
                DB.patients().deleteById(owner.getId());
            }
        }
    }

    private static PendingIntent findToken(Context context, EventReminder reminder) {
        return PendingIntent.getBroadcast(context, 0,
                Agenda.reminderBroadcastIntent(context, reminder),
                PendingIntentFlags.immutable(PendingIntent.FLAG_NO_CREATE));
    }
}
