/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util.alerts;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.dmfs.rfc5545.recur.Freq;
import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.persistence.alerts.AllergyPatientAlert;
import es.usc.citius.servando.calendula.scheduling.Agenda;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.recur.RecurringEvent;

import static org.junit.Assert.*;

/**
 * Disposable SQLite fixtures only: intentionally fail Android reconciliation
 * after alert and schedule SQLite COMMIT, then simulate an app-start retry.
 * No live clinical or authenticated patient records.
 */
@RunWith(AndroidJUnit4.class)
public class ScheduleAlarmRecoverySmokeTest {
    @Test
    public void failedPostCommitUnblockKeepsDurableMarkerUntilSuccessfulStartupRetry()
            throws Exception {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String suffix = Long.toString(System.nanoTime());
        Patient owner = new Patient();
        owner.setCode("ci-recovery-owner-" + suffix);
        owner.setName("Synthetic schedule recovery owner");
        Medicine medicine = new Medicine("Synthetic blocked medicine", Presentation.PILLS);
        Schedule schedule = null;
        AllergyPatientAlert warning = new AllergyPatientAlert();
        SQLiteDatabase sqlite = DB.helper().getWritableDatabase();
        String trigger = "ci_recovery_occurrence_abort";
        sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
        try {
            assertEquals(1, DB.patients().create(owner));
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

            warning.setPatient(owner);
            warning.setMedicine(medicine);
            warning.setLevel(3);
            AlertManager.createAlert(warning);
            assertNotNull(DB.alerts().findById(warning.id()));
            assertTrue(DB.schedules().findById(schedule.getId())
                    .hasState(Schedule.ScheduleState.BLOCKED));

            sqlite.execSQL("CREATE TRIGGER " + trigger
                    + " BEFORE INSERT ON EventInstances WHEN NEW.Ref = "
                    + schedule.getId()
                    + " BEGIN SELECT RAISE(ABORT, 'ci simulated postcommit crash'); END;");
            boolean reported = false;
            try {
                AlertManager.removeAlert(warning);
            } catch (RuntimeException expected) {
                reported = true;
            }
            assertTrue("OS reconciliation failure must be reported", reported);
            assertNull("Alert SQL deletion already committed", DB.alerts().findById(warning.id()));
            Schedule committed = DB.schedules().findById(schedule.getId());
            assertFalse("Schedule release already committed",
                    committed.hasState(Schedule.ScheduleState.BLOCKED));
            assertTrue("Recovery obligation must survive the failed callback",
                    committed.hasState(Schedule.ScheduleState.PENDING_REMINDER_RECOVERY));
            assertEquals("Recovery owner must be durably bound before callbacks",
                    ScheduleAlarmRecovery.ownerDigest(owner),
                    committed.getRecoveryOwnerDigest());
            assertTrue("Failed occurrence INSERT must not leave a partial batch",
                    DB.eventInstances().findBy(EventInstance.COLUMN_REF,
                            schedule.getId()).isEmpty());

            // A restored/recycled SQLite ID must never inherit an earlier
            // patient's saved recovery obligation. Alter only the digest in
            // this disposable fixture, leaving all original patient records.
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            Patient replacementOwner = new Patient();
            replacementOwner.setId(owner.getId());
            replacementOwner.setCode("ci-replaced-different-owner-" + suffix);
            // Corrupt only the on-disk owner binding as a restored legacy
            // fixture would. Avoid an ORM update of the complete schedule:
            // it also carries unrelated dosage and recurrence fields.
            sqlite.execSQL("UPDATE NewSchedules SET RecoveryOwnerDigest=? WHERE _id=?",
                    new Object[]{ScheduleAlarmRecovery.ownerDigest(replacementOwner),
                            schedule.getId()});
            assertEquals(ScheduleAlarmRecovery.ownerDigest(replacementOwner),
                    DB.schedules().findById(schedule.getId()).getRecoveryOwnerDigest());
            Agenda.instance().updateAllAlarms(context);
            assertTrue("Mismatched recovery owner must retain pending marker",
                    DB.schedules().findById(schedule.getId())
                            .hasState(Schedule.ScheduleState.PENDING_REMINDER_RECOVERY));
            assertTrue("Recycled owner must not receive old medication intakes",
                    DB.eventInstances().findBy(
                            EventInstance.COLUMN_REF, schedule.getId()).isEmpty());

            // Restore the original digest and simulate a later app launch.
            sqlite.execSQL("UPDATE NewSchedules SET RecoveryOwnerDigest=? WHERE _id=?",
                    new Object[]{ScheduleAlarmRecovery.ownerDigest(owner),
                            schedule.getId()});
            assertEquals(ScheduleAlarmRecovery.ownerDigest(owner),
                    DB.schedules().findById(schedule.getId()).getRecoveryOwnerDigest());

            // An outstanding official-treatment review must suppress future
            // intakes, even when this patient's HIGH allergy alert is gone.
            // The pending marker is kept so review can be followed by retry.
            sqlite.execSQL("UPDATE NewSchedules SET State=? WHERE _id=?",
                    new Object[]{"[\"PENDING_REMINDER_RECOVERY\","
                            + "\"PENDING_REVIEW_AFTER_UPDATE\"]",
                            schedule.getId()});
            Agenda.instance().updateAllAlarms(context);
            assertTrue("Unreviewed prescription remains in recovery state",
                    DB.schedules().findById(schedule.getId())
                            .hasState(Schedule.ScheduleState.PENDING_REMINDER_RECOVERY));
            assertTrue("Unreviewed medication must not generate a dose",
                    DB.eventInstances().findBy(
                            EventInstance.COLUMN_REF, schedule.getId()).isEmpty());

            // After a separately authorized review resolves the hold, boot
            // replay may resume the verified patient's original schedule.
            sqlite.execSQL("UPDATE NewSchedules SET State=? WHERE _id=?",
                    new Object[]{"[\"PENDING_REMINDER_RECOVERY\"]",
                            schedule.getId()});
            Agenda.instance().updateAllAlarms(context);
            committed = DB.schedules().findById(schedule.getId());
            assertFalse("Clear marker only after successful OS reconciliation",
                    committed.hasState(Schedule.ScheduleState.PENDING_REMINDER_RECOVERY));
            assertNull("Never retain an unnecessary owner fingerprint",
                    committed.getRecoveryOwnerDigest());
            int restored = DB.eventInstances().findBy(
                    EventInstance.COLUMN_REF, schedule.getId()).size();
            assertTrue("Rebuilt schedule must have future synthetic doses", restored > 0);
            Agenda.instance().updateAllAlarms(context);
            assertEquals("Reboot retry must never duplicate the same dose",
                    restored, DB.eventInstances().findBy(
                            EventInstance.COLUMN_REF, schedule.getId()).size());

            // Simulate a harder interruption: SQL generated future events,
            // one synthetic dose was marked completed, but the recovery
            // marker was never cleared before process death. A later replay
            // must preserve the completed decision and avoid duplicates.
            EventInstance completed = DB.eventInstances().findBy(
                    EventInstance.COLUMN_REF, schedule.getId()).get(0);
            sqlite.execSQL("UPDATE EventInstances SET Completed=1 WHERE _id=?",
                    new Object[]{completed.getId()});
            assertTrue(DB.eventInstances().findById(completed.getId()).completed());
            sqlite.execSQL("UPDATE NewSchedules SET State=?, RecoveryOwnerDigest=?"
                            + " WHERE _id=?",
                    new Object[]{"[\"PENDING_REMINDER_RECOVERY\"]",
                            ScheduleAlarmRecovery.ownerDigest(owner), schedule.getId()});
            assertTrue(DB.schedules().findById(schedule.getId())
                    .hasState(Schedule.ScheduleState.PENDING_REMINDER_RECOVERY));
            Agenda.instance().updateAllAlarms(context);
            assertTrue("A completed dose must remain completed after replay",
                    DB.eventInstances().findById(completed.getId()).completed());
            assertEquals("Restart must not duplicate historic or pending doses",
                    restored, DB.eventInstances().findBy(
                            EventInstance.COLUMN_REF, schedule.getId()).size());
            assertFalse(DB.schedules().findById(schedule.getId())
                    .hasState(Schedule.ScheduleState.PENDING_REMINDER_RECOVERY));
        } finally {
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            if (warning.id() != null && DB.alerts().findById(warning.id()) != null) {
                DB.alerts().deleteById(warning.id());
            }
            if (schedule != null && schedule.getId() != null) {
                DB.eventInstances().removeByRef(schedule.getId());
                if (DB.schedules().findById(schedule.getId()) != null) {
                    DB.schedules().deleteById(schedule.getId());
                }
            }
            try {
                Agenda.instance().cleanReminders(context);
            } catch (RuntimeException ignored) {
                // Disposable synthetic test database teardown only.
            }
            if (medicine.getId() != null && DB.medicines().findById(medicine.getId()) != null) {
                DB.medicines().deleteById(medicine.getId());
            }
            if (owner.getId() != null && DB.patients().findById(owner.getId()) != null) {
                DB.patients().deleteById(owner.getId());
            }
        }
    }

    @Test
    public void recoveryPolicyRejectsRecycledOwnerAndStillBlockedSchedule() {
        Patient original = new Patient();
        original.setId(81290L);
        original.setCode("ci-original-owner");
        Patient successor = new Patient();
        successor.setId(81290L);
        successor.setCode("ci-recycled-owner");
        Medicine medicine = new Medicine("Synthetic guarded medicine", Presentation.PILLS);
        medicine.setId(8823L);
        medicine.setPatient(original);
        Schedule schedule = new Schedule(medicine);
        schedule.setId(4490L);
        schedule.setPatient(original);
        schedule.addState(Schedule.ScheduleState.PENDING_REMINDER_RECOVERY);
        schedule.setRecoveryOwnerDigest(ScheduleAlarmRecovery.ownerDigest(original));
        assertTrue(ScheduleAlarmRecovery.eligible(
                schedule, original, original, medicine, false));
        assertFalse(ScheduleAlarmRecovery.eligible(
                schedule, original, successor, medicine, false));
        schedule.setRecoveryOwnerDigest(ScheduleAlarmRecovery.ownerDigest(successor));
        assertFalse("Original patient digest must be required",
                ScheduleAlarmRecovery.eligible(
                        schedule, original, original, medicine, false));
        schedule.setRecoveryOwnerDigest(ScheduleAlarmRecovery.ownerDigest(original));
        assertFalse(ScheduleAlarmRecovery.eligible(
                schedule, original, original, medicine, true));
        schedule.addState(Schedule.ScheduleState.BLOCKED);
        assertFalse(ScheduleAlarmRecovery.eligible(
                schedule, original, original, medicine, false));
        schedule.removeState(Schedule.ScheduleState.BLOCKED);
        schedule.removeState(Schedule.ScheduleState.PENDING_REMINDER_RECOVERY);
        assertFalse(ScheduleAlarmRecovery.eligible(
                schedule, original, original, medicine, false));
    }
}
