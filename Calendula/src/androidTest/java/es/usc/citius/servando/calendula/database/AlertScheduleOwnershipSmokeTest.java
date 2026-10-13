/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.database;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.persistence.Schedule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Synthetic Android SQLite ownership checks for blocked-schedule alerts. */
@RunWith(AndroidJUnit4.class)
public class AlertScheduleOwnershipSmokeTest {
    @Test
    public void medicineDeletionCannotCascadeIntoAnotherPatientsSchedule() throws Exception {
        assertTrue(DB.initialized);
        String seed = Long.toString(System.nanoTime());
        Patient owner = new Patient();
        owner.setCode("ci-med-cascade-owner-" + seed);
        owner.setName("Synthetic cascade owner");
        Patient foreign = new Patient();
        foreign.setCode("ci-med-cascade-foreign-" + seed);
        foreign.setName("Synthetic cascade foreign");
        Medicine medicine = new Medicine("Synthetic medicine cascade", Presentation.PILLS);
        Schedule ownSchedule = new Schedule(medicine);
        Schedule foreignSchedule = new Schedule(medicine);
        try {
            assertEquals(1, DB.patients().create(owner));
            assertEquals(1, DB.patients().create(foreign));
            medicine.setPatient(owner);
            assertEquals(1, DB.medicines().create(medicine));
            ownSchedule.setPatient(owner);
            foreignSchedule.setPatient(foreign);
            assertEquals(1, DB.schedules().create(ownSchedule));
            assertEquals(1, DB.schedules().findByMedicine(medicine).size());
            // Simulate a damaged import referencing A's medicine from B's
            // prescription. Any cascade must reject *before* deleting A.
            assertEquals(1, DB.schedules().create(foreignSchedule));
            boolean searchRejected = false;
            try {
                DB.schedules().findByMedicine(medicine);
            } catch (IllegalStateException expected) {
                searchRejected = true;
            }
            assertTrue("Cross-owner medication query must reject invalid schedule", searchRejected);
            boolean deletionRejected = false;
            try {
                DB.medicines().deleteCascade(medicine, false);
            } catch (RuntimeException expected) {
                deletionRejected = true;
            }
            assertTrue("Medicine deletion must abort rather than erase B's schedule",
                    deletionRejected);
            assertNotNull(DB.medicines().findById(medicine.getId()));
            assertNotNull(DB.schedules().findById(ownSchedule.getId()));
            assertNotNull(DB.schedules().findById(foreignSchedule.getId()));
        } finally {
            for (Schedule sc : new Schedule[]{ownSchedule, foreignSchedule}) {
                if (sc.getId() != null && DB.schedules().findById(sc.getId()) != null) {
                    DB.schedules().remove(sc);
                }
            }
            if (medicine.getId() != null && DB.medicines().findById(medicine.getId()) != null) {
                DB.medicines().remove(medicine);
            }
            for (Patient patient : new Patient[]{owner, foreign}) {
                if (patient.getId() != null && DB.patients().findById(patient.getId()) != null) {
                    DB.patients().remove(patient);
                }
            }
        }
    }

    @Test
    public void alertScheduleSearchRefusesReassignedOrDeletedMedicine() throws Exception {
        assertTrue(DB.initialized);
        String seed = Long.toString(System.nanoTime());
        Patient first = new Patient();
        first.setCode("ci-alert-owner-a-" + seed);
        first.setName("Synthetic alert owner A");
        Patient second = new Patient();
        second.setCode("ci-alert-owner-b-" + seed);
        second.setName("Synthetic alert owner B");
        Medicine medicine = new Medicine("Synthetic blocked alert medicine", Presentation.PILLS);
        Schedule blocked = new Schedule(medicine);
        try {
            DB.patients().create(first);
            DB.patients().create(second);
            medicine.setPatient(first);
            DB.medicines().create(medicine);
            blocked.setPatient(first);
            blocked.addState(Schedule.ScheduleState.BLOCKED);
            DB.schedules().create(blocked);

            assertEquals("Original patient may find their blocked schedule",
                    1, DB.schedules().findByMedicineAndState(
                            medicine, Schedule.ScheduleState.BLOCKED).size());
            assertTrue("A missing state must fail closed",
                    DB.schedules().findByMedicineAndState(medicine, null).isEmpty());
            assertTrue("A missing medicine cannot produce patient alerts",
                    DB.schedules().findByMedicineAndState(
                            null, Schedule.ScheduleState.BLOCKED).isEmpty());

            // The stale in-memory medicine still belongs to A; its SQLite
            // owner is now B. It must never authorize A's clinical plan.
            DB.helper().getWritableDatabase().execSQL(
                    "UPDATE Medicines SET Patient = ? WHERE _id = ?",
                    new Object[]{second.getId(), medicine.getId()});
            assertTrue("A stale medicine must not yield A's blocked schedule",
                    DB.schedules().findByMedicineAndState(
                            medicine, Schedule.ScheduleState.BLOCKED).isEmpty());
        } finally {
            if (blocked.getId() != null && DB.schedules().findById(blocked.getId()) != null) {
                DB.schedules().remove(blocked);
            }
            if (medicine.getId() != null && DB.medicines().findById(medicine.getId()) != null) {
                DB.medicines().remove(medicine);
            }
            for (Patient p : new Patient[]{first, second}) {
                if (p.getId() != null && DB.patients().findById(p.getId()) != null) {
                    DB.patients().remove(p);
                }
            }
        }
    }
}
