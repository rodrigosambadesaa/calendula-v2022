/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util.alerts;

import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.dmfs.rfc5545.recur.Freq;
import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Collections;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.PatientAlert;
import es.usc.citius.servando.calendula.persistence.alerts.DrivingCautionAlert;
import es.usc.citius.servando.calendula.persistence.alerts.StockRunningOutAlert;
import org.joda.time.LocalDate;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.persistence.alerts.AllergyPatientAlert;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventType;
import es.usc.citius.servando.calendula.scheduling.model.recur.RecurringEvent;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AtomicHighAlertSmokeTest {
    @Test
    public void highAlertCannotBlockMedicationOwnedByAnotherPatient() throws Exception {
        assertTrue(DB.initialized);
        String suffix = Long.toString(System.nanoTime());
        Patient owner = new Patient();
        owner.setCode("ci-high-owner-" + suffix);
        owner.setName("Synthetic medicine owner");
        Patient unrelated = new Patient();
        unrelated.setCode("ci-high-unrelated-" + suffix);
        unrelated.setName("Synthetic unrelated patient");
        Medicine medicine = new Medicine("Synthetic owner-scoped medicine", Presentation.PILLS);
        AllergyPatientAlert incorrect = new AllergyPatientAlert();
        try {
            assertEquals(1, DB.patients().create(owner));
            assertEquals(1, DB.patients().create(unrelated));
            medicine.setPatient(owner);
            assertEquals(1, DB.medicines().create(medicine));

            incorrect.setLevel(3);
            incorrect.setPatient(unrelated);
            incorrect.setMedicine(medicine);
            boolean rejected = false;
            try {
                AlertManager.createAlert(incorrect);
            } catch (RuntimeException expected) {
                rejected = true;
            }
            assertTrue("A HIGH alert must never cross a patient boundary", rejected);
            if (incorrect.id() != null) {
                assertNull("Rejected cross-patient alert must not be stored",
                        DB.alerts().findById(incorrect.id()));
            }
        } finally {
            if (incorrect.id() != null && DB.alerts().findById(incorrect.id()) != null) {
                DB.alerts().remove(incorrect);
            }
            if (medicine.getId() != null && DB.medicines().findById(medicine.getId()) != null) {
                DB.medicines().remove(medicine);
            }
            for (Patient patient : new Patient[]{owner, unrelated}) {
                if (patient.getId() != null && DB.patients().findById(patient.getId()) != null) {
                    DB.patients().remove(patient);
                }
            }
        }
    }

    @Test
    public void lowAndMediumAlertsRejectRecycledOwnerAndKeepGoodRecords()
            throws Exception {
        assertTrue(DB.initialized);
        final String suffix = Long.toString(System.nanoTime());
        Patient owner = new Patient();
        owner.setCode("ci-alert-all-severity-owner-" + suffix);
        owner.setName("Synthetic severity owner");
        Patient other = new Patient();
        other.setCode("ci-alert-all-severity-foreign-" + suffix);
        other.setName("Synthetic unrelated severity owner");
        Medicine medicine = new Medicine("Synthetic alert validation med",
                Presentation.PILLS);
        try {
            assertEquals(1, DB.patients().create(owner));
            assertEquals(1, DB.patients().create(other));
            medicine.setPatient(owner);
            assertEquals(1, DB.medicines().create(medicine));

            // Two actual alert classes: driving is LOW, stock is MEDIUM.
            PatientAlert[] candidates = new PatientAlert[]{
                    new DrivingCautionAlert(medicine),
                    new StockRunningOutAlert(medicine, LocalDate.now())};
            Patient recycledId = new Patient();
            recycledId.setId(owner.getId());
            recycledId.setCode("ci-alert-recycled-code-" + suffix);
            for (PatientAlert alert : candidates) {
                int expectedLevel = alert.getLevel();
                assertTrue(expectedLevel == PatientAlert.Level.LOW
                        || expectedLevel == PatientAlert.Level.MEDIUM);

                for (Patient wrong : new Patient[]{other, recycledId}) {
                    alert.setPatient(wrong);
                    boolean rejected = false;
                    try {
                        AlertManager.createAlert(alert);
                    } catch (RuntimeException expected) {
                        rejected = true;
                    }
                    assertTrue("Every alert severity must fail closed on a foreign owner",
                            rejected);
                    assertNull("A rejected alert must not retain a phantom DB row ID",
                            alert.id());
                    assertTrue("Rejected alert must not write any rows",
                            DB.alerts().findByMedicineAndType(medicine, alert.getType())
                                    .isEmpty());
                }

                // A valid persisted owner still works; a hardening guard must
                // not suppress genuine inventory or driving safety warnings.
                alert.setPatient(owner);
                AlertManager.createAlert(alert);
                assertNotNull(alert.id());
                assertNotNull(DB.alerts().findById(alert.id()));
                AlertManager.removeAlert(alert);
                assertNull(DB.alerts().findById(alert.id()));
            }
        } finally {
            if (medicine.getId() != null) {
                for (PatientAlert alert : DB.alerts().findBy(
                        PatientAlert.COLUMN_MEDICINE, medicine.getId())) {
                    DB.alerts().remove(alert);
                }
                if (DB.medicines().findById(medicine.getId()) != null) {
                    DB.medicines().remove(medicine);
                }
            }
            for (Patient patient : new Patient[]{owner, other}) {
                if (patient.getId() != null
                        && DB.patients().findById(patient.getId()) != null) {
                    DB.patients().remove(patient);
                }
            }
        }
    }

    @Test
    public void secondHighWarningKeepsScheduleBlockedUntilTheLastWarningIsRemoved()
            throws Exception {
        assertTrue(DB.initialized);
        String suffix = Long.toString(System.nanoTime());
        Patient owner = new Patient();
        owner.setCode("ci-high-shared-owner-" + suffix);
        owner.setName("Synthetic shared warning owner");
        Medicine medicine = new Medicine("Synthetic shared warning medicine",
                Presentation.PILLS);
        Schedule schedule = null;
        AllergyPatientAlert first = new AllergyPatientAlert();
        AllergyPatientAlert second = new AllergyPatientAlert();
        try {
            assertEquals(1, DB.patients().create(owner));
            medicine.setPatient(owner);
            assertEquals(1, DB.medicines().create(medicine));
            RecurringEvent recur = new RecurringEvent.Builder()
                    .from(DateTime.now().minusDays(1))
                    .repeatEvery(12, Freq.HOURLY).build();
            schedule = new Schedule(recur);
            schedule.setPatient(owner);
            schedule.setMedicine(medicine);
            schedule.setDosage(1.0d);
            assertEquals(1, DB.schedules().create(schedule));

            for (AllergyPatientAlert warning :
                    new AllergyPatientAlert[]{first, second}) {
                warning.setMedicine(medicine);
                warning.setPatient(owner);
                warning.setLevel(3);
                AlertManager.createAlert(warning);
            }
            assertTrue(DB.schedules().findById(schedule.getId())
                    .hasState(Schedule.ScheduleState.BLOCKED));
            AlertManager.removeAlert(first);
            assertNull(DB.alerts().findById(first.id()));
            assertNotNull("The remaining HIGH warning must survive",
                    DB.alerts().findById(second.id()));
            assertTrue("The last HIGH warning still blocks the treatment",
                    DB.schedules().findById(schedule.getId())
                            .hasState(Schedule.ScheduleState.BLOCKED));

            AlertManager.removeAlert(second);
            assertNull(DB.alerts().findById(second.id()));
            assertFalse("Last HIGH warning removal releases the schedule",
                    DB.schedules().findById(schedule.getId())
                            .hasState(Schedule.ScheduleState.BLOCKED));
        } finally {
            for (AllergyPatientAlert warning :
                    new AllergyPatientAlert[]{first, second}) {
                if (warning.id() != null
                        && DB.alerts().findById(warning.id()) != null) {
                    DB.alerts().remove(warning);
                }
            }
            if (schedule != null && schedule.getId() != null
                    && DB.schedules().findById(schedule.getId()) != null) {
                DB.eventInstances().removeByRef(schedule.getId());
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

    @Test
    public void failedSecondIntakeDeleteRollsBackAlertAndBothBlockedSchedules()
            throws Exception {
        assertTrue(DB.initialized);
        String suffix = Long.toString(System.nanoTime());
        Patient owner = new Patient();
        owner.setName("Synthetic HIGH alert owner");
        owner.setCode("ci-high-alert-rollback-" + suffix);
        Medicine medicine = new Medicine("Synthetic alert medicine", Presentation.PILLS);
        Schedule first = null;
        Schedule second = null;
        EventInstance one = null;
        EventInstance two = null;
        AllergyPatientAlert failedAlert = new AllergyPatientAlert();
        SQLiteDatabase sqlite = DB.helper().getWritableDatabase();
        final String trigger = "ci_block_second_alert_event_abort";
        sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
        try {
            assertEquals(1, DB.patients().create(owner));
            medicine.setPatient(owner);
            assertEquals(1, DB.medicines().create(medicine));

            RecurringEvent recur = new RecurringEvent.Builder()
                    .from(DateTime.now().minusDays(1))
                    .repeatEvery(8, Freq.HOURLY).build();
            first = new Schedule(recur);
            first.setPatient(owner);
            first.setMedicine(medicine);
            first.setDosage(1.0d);
            second = new Schedule(recur);
            second.setPatient(owner);
            second.setMedicine(medicine);
            second.setDosage(1.0d);
            assertEquals(1, DB.schedules().create(first));
            assertEquals(1, DB.schedules().create(second));
            one = new EventInstance(DateTime.now().plusMinutes(25),
                    EventType.MEDICATION_INTAKE);
            two = new EventInstance(DateTime.now().plusMinutes(30),
                    EventType.MEDICATION_INTAKE);
            one.setPatient(owner);
            two.setPatient(owner);
            one.setRef(first.getId());
            two.setRef(second.getId());
            one.addParam(EventInstance.PARAM_DOSE, 1.0d);
            two.addParam(EventInstance.PARAM_DOSE, 1.0d);
            assertEquals(1, DB.eventInstances().create(one));
            assertEquals(1, DB.eventInstances().create(two));
            failedAlert.setMedicine(medicine);
            failedAlert.setPatient(owner);
            failedAlert.setLevel(3);
            sqlite.execSQL("CREATE TRIGGER " + trigger
                    + " BEFORE DELETE ON EventInstances WHEN OLD._id = " + two.getId()
                    + " BEGIN SELECT RAISE(ABORT, 'synthetic second event failure'); END;");
            boolean rejected = false;
            try {
                AlertManager.createAlert(failedAlert);
            } catch (RuntimeException expected) {
                rejected = true;
            }
            assertTrue("HIGH alert must reject an incomplete schedule-blocking write", rejected);
            assertNull("A rolled-back new alert must retain no generated SQL ID",
                    failedAlert.id());
            Schedule storedFirst = DB.schedules().findById(first.getId());
            Schedule storedSecond = DB.schedules().findById(second.getId());
            assertNotNull(storedFirst);
            assertNotNull(storedSecond);
            assertFalse("First schedule BLOCKED state must roll back",
                    storedFirst.hasState(Schedule.ScheduleState.BLOCKED));
            assertFalse("Second schedule BLOCKED state must roll back",
                    storedSecond.hasState(Schedule.ScheduleState.BLOCKED));
            assertNotNull("First patient's pending dose must roll back",
                    DB.eventInstances().findById(one.getId()));
            assertNotNull("Second patient's pending dose must roll back",
                    DB.eventInstances().findById(two.getId()));

            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            // Retry the EXACT original Java draft. A rolled-back auto-ID
            // must not force createOrUpdate into a stale UPDATE.
            AlertManager.createAlert(failedAlert);
            assertNotNull("Retry must insert the clinical alert",
                    DB.alerts().findById(failedAlert.id()));
            assertTrue(DB.schedules().findById(first.getId())
                    .hasState(Schedule.ScheduleState.BLOCKED));
            assertTrue(DB.schedules().findById(second.getId())
                    .hasState(Schedule.ScheduleState.BLOCKED));
            assertNull(DB.eventInstances().findById(one.getId()));
            assertNull(DB.eventInstances().findById(two.getId()));

            // A failure unblocking the SECOND schedule must roll back the
            // alert deletion and the FIRST schedule's unblocking as well.
            // No future occurrences may be regenerated before that commit.
            final String unblockTrigger = "ci_unblock_second_schedule_abort";
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + unblockTrigger);
            sqlite.execSQL("CREATE TRIGGER " + unblockTrigger
                    + " BEFORE UPDATE ON NewSchedules WHEN OLD._id = "
                    + second.getId()
                    + " BEGIN SELECT RAISE(ABORT, 'synthetic unblock failure'); END;");
            try {
                rejected = false;
                try {
                    AlertManager.removeAlert(failedAlert);
                } catch (RuntimeException expected) {
                    rejected = true;
                }
                assertTrue("Partial HIGH alert deletion must fail", rejected);
                assertNotNull("HIGH alert must survive failed schedule release",
                        DB.alerts().findById(failedAlert.id()));
                assertTrue("First blocked schedule must roll back",
                        DB.schedules().findById(first.getId())
                                .hasState(Schedule.ScheduleState.BLOCKED));
                assertTrue("Second blocked schedule must roll back",
                        DB.schedules().findById(second.getId())
                                .hasState(Schedule.ScheduleState.BLOCKED));
                assertNull("No new intake may be regenerated before COMMIT",
                        DB.eventInstances().findById(one.getId()));
            } finally {
                sqlite.execSQL("DROP TRIGGER IF EXISTS " + unblockTrigger);
            }

            // Retry using the original alert object after removing the
            // injected failure: delete and both releases must commit.
            AlertManager.removeAlert(failedAlert);
            assertNull("HIGH warning must be removed on successful retry",
                    DB.alerts().findById(failedAlert.id()));
            assertFalse(DB.schedules().findById(first.getId())
                    .hasState(Schedule.ScheduleState.BLOCKED));
            assertFalse(DB.schedules().findById(second.getId())
                    .hasState(Schedule.ScheduleState.BLOCKED));
        } finally {
            sqlite.execSQL("DROP TRIGGER IF EXISTS ci_unblock_second_schedule_abort");
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            if (failedAlert.id() != null
                    && DB.alerts().findById(failedAlert.id()) != null) {
                DB.alerts().remove(failedAlert);
            }
            for (EventInstance e : new EventInstance[]{one, two}) {
                if (e != null && e.getId() != null
                        && DB.eventInstances().findById(e.getId()) != null) {
                    DB.eventInstances().remove(e);
                }
            }
            for (Schedule schedule : new Schedule[]{first, second}) {
                if (schedule != null && schedule.getId() != null
                        && DB.schedules().findById(schedule.getId()) != null) {
                    DB.eventInstances().removeByRef(schedule.getId());
                    DB.schedules().remove(schedule);
                }
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
    public void createAlertMustNotOverwriteExistingRecordWithSuppliedId()
            throws Exception {
        assertTrue(DB.initialized);
        String suffix = Long.toString(System.nanoTime());
        Patient patientA = new Patient();
        patientA.setCode("ci-alert-insert-a-" + suffix);
        patientA.setName("Synthetic alert record A");
        Patient patientB = new Patient();
        patientB.setCode("ci-alert-insert-b-" + suffix);
        patientB.setName("Synthetic alert record B");
        Medicine medicineA = new Medicine("Synthetic insert-only A", Presentation.PILLS);
        Medicine medicineB = new Medicine("Synthetic insert-only B", Presentation.PILLS);
        PatientAlert existing = null;
        PatientAlert validB = null;
        try {
            assertEquals(1, DB.patients().create(patientA));
            assertEquals(1, DB.patients().create(patientB));
            medicineA.setPatient(patientA);
            medicineB.setPatient(patientB);
            assertEquals(1, DB.medicines().create(medicineA));
            assertEquals(1, DB.medicines().create(medicineB));

            existing = new DrivingCautionAlert(medicineA);
            assertNull(existing.id());
            AlertManager.createAlert(existing);
            assertNotNull(existing.id());

            // The previous createOrUpdate implementation silently updated A's
            // record when an otherwise valid B-owned draft carried A's ID.
            PatientAlert attemptedOverwrite = new StockRunningOutAlert(
                    medicineB, LocalDate.now());
            attemptedOverwrite.setId(existing.id());
            boolean rejected = false;
            try {
                AlertManager.createAlert(attemptedOverwrite);
            } catch (RuntimeException expected) {
                rejected = true;
            }
            assertTrue("Create must reject a caller-supplied existing alert ID", rejected);
            PatientAlert originalRow = DB.alerts().findById(existing.id());
            assertNotNull("Patient A's alert must survive the rejected overwrite",
                    originalRow);
            assertEquals(patientA.getId(), originalRow.getPatient().getId());
            assertEquals(medicineA.getId(), originalRow.getMedicine().getId());
            assertEquals(DrivingCautionAlert.class.getCanonicalName(),
                    originalRow.getType());
            assertEquals(PatientAlert.Level.LOW, originalRow.getLevel());
            assertTrue("Patient B must not acquire a row by overwriting patient A",
                    DB.alerts().findByMedicineAndType(
                            medicineB, StockRunningOutAlert.class.getCanonicalName())
                            .isEmpty());

            // This is creation-only hardening, not a prohibition on legitimate
            // fresh LOW/MEDIUM alerts for a different patient.
            validB = new StockRunningOutAlert(medicineB, LocalDate.now());
            assertNull(validB.id());
            AlertManager.createAlert(validB);
            assertNotNull("Fresh B-owned warning must still be insertable",
                    DB.alerts().findById(validB.id()));
            assertNotNull("A's existing record must remain untouched",
                    DB.alerts().findById(existing.id()));
        } finally {
            for (Medicine medicine : new Medicine[]{medicineA, medicineB}) {
                if (medicine.getId() == null) {
                    continue;
                }
                for (PatientAlert warning : DB.alerts().findBy(
                        PatientAlert.COLUMN_MEDICINE, medicine.getId())) {
                    DB.alerts().deleteById(warning.id());
                }
            }
            if (existing != null && existing.id() != null
                    && DB.alerts().findById(existing.id()) != null) {
                DB.alerts().deleteById(existing.id());
            }
            for (Medicine medicine : new Medicine[]{medicineA, medicineB}) {
                if (medicine.getId() != null
                        && DB.medicines().findById(medicine.getId()) != null) {
                    DB.medicines().deleteById(medicine.getId());
                }
            }
            for (Patient patient : new Patient[]{patientA, patientB}) {
                if (patient.getId() != null
                        && DB.patients().findById(patient.getId()) != null) {
                    DB.patients().deleteById(patient.getId());
                }
            }
        }
    }

}
