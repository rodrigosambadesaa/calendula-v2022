/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.database;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.joda.time.LocalDate;
import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.PatientAlert;
import es.usc.citius.servando.calendula.persistence.PickupInfo;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.persistence.alerts.StockRunningOutAlert;

import static org.junit.Assert.*;

/** Transactional deletion regression tests using only uniquely named fixture rows. */
@RunWith(AndroidJUnit4.class)
public class AtomicMedicineCascadeSmokeTest {

    @Test
    public void deleteRemovesOwnedSchedulesPickupsAndAlertsTogether() throws Exception {
        assertTrue(DB.initialized);
        Patient owner = makePatient("cascade-owner");
        Medicine medicine = new Medicine("Synthetic cascade medicine", Presentation.PILLS);
        PickupInfo pickup = new PickupInfo();
        Schedule schedule = new Schedule();
        PatientAlert alert = null;
        try {
            assertEquals(1, DB.patients().create(owner));
            medicine.setPatient(owner);
            assertEquals(1, DB.medicines().create(medicine));
            assertNotNull(medicine.getId());

            schedule.setMedicine(medicine);
            schedule.setPatient(owner);
            assertEquals(1, DB.schedules().create(schedule));
            assertNotNull(schedule.getId());
            pickup.setMedicine(medicine);
            assertEquals(1, DB.pickups().create(pickup));
            assertNotNull(pickup.getId());
            alert = new StockRunningOutAlert(medicine, LocalDate.now());
            assertEquals(1, DB.alerts().create(alert));
            assertNotNull(alert.id());

            DB.medicines().deleteCascade(medicine, false);
            assertNull(DB.medicines().findById(medicine.getId()));
            assertNull(DB.schedules().findById(schedule.getId()));
            assertNull(DB.pickups().findById(pickup.getId()));
            assertNull(DB.alerts().findById(alert.id()));
        } finally {
            if (alert != null && alert.id() != null
                    && DB.alerts().findById(alert.id()) != null) DB.alerts().deleteById(alert.id());
            if (schedule.getId() != null && DB.schedules().findById(schedule.getId()) != null)
                DB.schedules().deleteById(schedule.getId());
            if (pickup.getId() != null && DB.pickups().findById(pickup.getId()) != null)
                DB.pickups().deleteById(pickup.getId());
            if (medicine.getId() != null && DB.medicines().findById(medicine.getId()) != null)
                DB.medicines().deleteById(medicine.getId());
            if (owner.getId() != null && DB.patients().findById(owner.getId()) != null)
                DB.patients().deleteById(owner.getId());
        }
    }

    @Test
    public void crossPatientAlertAbortsWholeCascadeWithoutLosingRows() throws Exception {
        assertTrue(DB.initialized);
        Patient owner = makePatient("cascade-owner-rollback");
        Patient stranger = makePatient("cascade-stranger");
        Medicine medicine = new Medicine("Synthetic rollback medicine", Presentation.PILLS);
        Schedule schedule = new Schedule();
        PatientAlert misplaced = null;
        try {
            assertEquals(1, DB.patients().create(owner));
            assertEquals(1, DB.patients().create(stranger));
            medicine.setPatient(owner);
            assertEquals(1, DB.medicines().create(medicine));
            schedule.setMedicine(medicine);
            schedule.setPatient(owner);
            assertEquals(1, DB.schedules().create(schedule));

            misplaced = new StockRunningOutAlert(medicine, LocalDate.now());
            misplaced.setPatient(stranger);
            assertEquals(1, DB.alerts().create(misplaced));
            boolean blocked = false;
            try {
                DB.medicines().deleteCascade(medicine, false);
            } catch (RuntimeException expected) {
                blocked = true;
            }
            assertTrue("Cross-patient alert prevents partial erasure", blocked);
            assertNotNull(DB.medicines().findById(medicine.getId()));
            assertNotNull(DB.schedules().findById(schedule.getId()));
            assertNotNull(DB.alerts().findById(misplaced.id()));
        } finally {
            if (misplaced != null && misplaced.id() != null
                    && DB.alerts().findById(misplaced.id()) != null)
                DB.alerts().deleteById(misplaced.id());
            if (schedule.getId() != null && DB.schedules().findById(schedule.getId()) != null)
                DB.schedules().deleteById(schedule.getId());
            if (medicine.getId() != null && DB.medicines().findById(medicine.getId()) != null)
                DB.medicines().deleteById(medicine.getId());
            if (stranger.getId() != null && DB.patients().findById(stranger.getId()) != null)
                DB.patients().deleteById(stranger.getId());
            if (owner.getId() != null && DB.patients().findById(owner.getId()) != null)
                DB.patients().deleteById(owner.getId());
        }
    }

    @Test
    public void sqliteFailureAfterDependencyDeletesRollsBackEntireCascade() throws Exception {
        assertTrue(DB.initialized);
        Patient owner = makePatient("cascade-rollback-sql");
        Medicine medicine = new Medicine("Synthetic SQL rollback medicine", Presentation.PILLS);
        Schedule schedule = new Schedule();
        PickupInfo pickup = new PickupInfo();
        PatientAlert alert = null;
        String trigger = "ci_abort_med_cascade_" + Long.toString(System.nanoTime()).replace("-", "");
        boolean triggerCreated = false;
        try {
            assertEquals(1, DB.patients().create(owner));
            medicine.setPatient(owner);
            assertEquals(1, DB.medicines().create(medicine));
            schedule.setMedicine(medicine);
            schedule.setPatient(owner);
            assertEquals(1, DB.schedules().create(schedule));
            pickup.setMedicine(medicine);
            assertEquals(1, DB.pickups().create(pickup));
            alert = new StockRunningOutAlert(medicine, LocalDate.now());
            assertEquals(1, DB.alerts().create(alert));

            // Persisted trigger is seen by ORMLite even if it opens another
            // SQLite connection. It applies only to this synthetic medicine.
            DB.helper().getWritableDatabase().execSQL(
                    "CREATE TRIGGER " + trigger
                    + " BEFORE DELETE ON Medicines WHEN OLD._id = "
                    + medicine.getId()
                    + " BEGIN SELECT RAISE(ABORT, 'injected cascade failure'); END");
            triggerCreated = true;
            boolean failed = false;
            try {
                DB.medicines().deleteCascade(medicine, false);
            } catch (RuntimeException expected) {
                failed = true;
            }
            assertTrue("Injected final medicine delete must fail", failed);
            assertNotNull("Medicine must be restored", DB.medicines().findById(medicine.getId()));
            assertNotNull("Schedule must be restored", DB.schedules().findById(schedule.getId()));
            assertNotNull("Pickup must be restored", DB.pickups().findById(pickup.getId()));
            assertNotNull("Alert must be restored", DB.alerts().findById(alert.id()));
        } finally {
            if (triggerCreated) {
                DB.helper().getWritableDatabase().execSQL("DROP TRIGGER IF EXISTS " + trigger);
            }
            if (alert != null && alert.id() != null
                    && DB.alerts().findById(alert.id()) != null) DB.alerts().deleteById(alert.id());
            if (pickup.getId() != null && DB.pickups().findById(pickup.getId()) != null)
                DB.pickups().deleteById(pickup.getId());
            if (schedule.getId() != null && DB.schedules().findById(schedule.getId()) != null)
                DB.schedules().deleteById(schedule.getId());
            if (medicine.getId() != null && DB.medicines().findById(medicine.getId()) != null)
                DB.medicines().deleteById(medicine.getId());
            if (owner.getId() != null && DB.patients().findById(owner.getId()) != null)
                DB.patients().deleteById(owner.getId());
        }
    }

    private static Patient makePatient(String label) {
        Patient patient = new Patient();
        patient.setCode("ci-" + label + "-" + System.nanoTime());
        patient.setName("Synthetic " + label);
        return patient;
    }
}
