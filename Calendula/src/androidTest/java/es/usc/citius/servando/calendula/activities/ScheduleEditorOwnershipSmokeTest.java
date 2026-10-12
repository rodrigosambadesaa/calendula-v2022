/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.util.ScheduleSaveOwnerGuard;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Synthetic SQLite owners only; never changes the app's active profile. */
@RunWith(AndroidJUnit4.class)
public class ScheduleEditorOwnershipSmokeTest {
    @Test
    public void reloadedOwnerAndMedicineCannotBeSilentlyReassignedAtSaveTime()
            throws Exception {
        assertTrue(DB.initialized);
        String seed = String.valueOf(System.nanoTime());
        Patient owner = new Patient();
        owner.setCode("ci-schedule-editor-owner-" + seed);
        owner.setName("Synthetic editor owner");
        Patient foreign = new Patient();
        foreign.setCode("ci-schedule-editor-other-" + seed);
        foreign.setName("Synthetic other owner");
        Medicine medicine = new Medicine("Synthetic schedule editor medicine",
                Presentation.PILLS);
        Schedule schedule = new Schedule(medicine);
        try {
            DB.patients().save(owner);
            DB.patients().save(foreign);
            medicine.setPatient(owner);
            DB.medicines().save(medicine);
            schedule.setPatient(owner);
            DB.schedules().save(schedule);
            assertNotNull(schedule.getId());

            Patient reloaded = DB.patients().findById(owner.getId());
            Medicine reloadedMedicine = DB.medicines().findById(medicine.getId());
            Schedule reloadedSchedule = DB.schedules().findById(schedule.getId());
            assertTrue(ScheduleSaveOwnerGuard.canSaveForOpenedPatient(
                    owner, reloaded, reloaded, reloadedMedicine));
            assertTrue(ScheduleSaveOwnerGuard.canEditSchedule(
                    owner, schedule, reloadedSchedule));
            assertFalse("Switching to B must reject background creation",
                    ScheduleSaveOwnerGuard.canSaveForOpenedPatient(
                            owner, foreign, reloaded, reloadedMedicine));
            assertFalse("Editing B's schedule from editor A is forbidden",
                    ScheduleSaveOwnerGuard.canEditSchedule(
                            foreign, schedule, reloadedSchedule));

            // Reassign medicine *in SQLite* after the editor selected it.
            // The save guard must trust the freshly reloaded medicine, not
            // an old Java object that still carries its former owner.
            DB.helper().getWritableDatabase().execSQL(
                    "UPDATE Medicines SET Patient = ? WHERE _id = ?",
                    new Object[]{foreign.getId(), medicine.getId()});
            reloadedMedicine = DB.medicines().findById(medicine.getId());
            assertFalse("Stale editor cannot save reassigned medicine",
                    ScheduleSaveOwnerGuard.canSaveForOpenedPatient(
                            owner, reloaded, reloaded, reloadedMedicine));
            assertNotNull("A failed guard did not change the stored schedule",
                    DB.schedules().findById(schedule.getId()));
        } finally {
            if (schedule.getId() != null && DB.schedules().findById(schedule.getId()) != null) {
                DB.schedules().remove(schedule);
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
}
