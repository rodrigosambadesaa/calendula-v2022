/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities.schedules;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.joda.time.LocalTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.List;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Routine;
import static org.junit.Assert.*;

/** Patient-scoped editor routine list on real Android SQLite. */
@RunWith(AndroidJUnit4.class)
public class ScheduleEditorRoutineScopeSmokeTest {
    @Test
    public void equalRoutineCountsNeverRevealAnotherPatientInStaleEditor() throws Exception {
        assertTrue(DB.initialized);
        String key = Long.toString(System.nanoTime());
        Patient first = new Patient();
        first.setCode("ci-routine-editor-A-" + key);
        first.setName("Synthetic opening editor A");
        Patient second = new Patient();
        second.setCode("ci-routine-editor-B-" + key);
        second.setName("Synthetic active user B");
        Routine aRoutine = null;
        Routine bRoutine = null;
        try {
            assertEquals(1, DB.patients().create(first));
            assertEquals(1, DB.patients().create(second));
            aRoutine = new Routine(first, new LocalTime(10, 15), "Synthetic routine A private");
            bRoutine = new Routine(second, new LocalTime(10, 15), "Synthetic routine B private");
            assertEquals(1, DB.routines().create(aRoutine));
            assertEquals(1, DB.routines().create(bRoutine));

            List<Routine> authorized = ScheduleEditorRoutineSource.load(first.getId(), first);
            assertEquals(1, authorized.size());
            assertEquals(aRoutine.getId(), authorized.get(0).getId());

            assertTrue("After switching to B, A's stale editor must show no B rows",
                    ScheduleEditorRoutineSource.load(first.getId(), second).isEmpty());
            assertNull("Cannot create custom times for the switched profile",
                    ScheduleEditorRoutineSource.authorizedPatient(first.getId(), second));
            assertTrue("No owner cannot reveal a default patient's routines",
                    ScheduleEditorRoutineSource.load(null, second).isEmpty());
            assertTrue("An unsaved opening editor owner cannot access routines",
                    ScheduleEditorRoutineSource.load(-1L, first).isEmpty());

            assertEquals(bRoutine.getId(),
                    ScheduleEditorRoutineSource.load(second.getId(), second).get(0).getId());
            DB.routines().remove(aRoutine);
            DB.patients().remove(first);
            assertTrue("Deleted original owner cannot be silently reassigned to active B",
                    ScheduleEditorRoutineSource.load(first.getId(), second).isEmpty());
            assertNotNull("B's separate routine must be untouched",
                    DB.routines().findById(bRoutine.getId()));
        } finally {
            for (Routine routine : new Routine[]{aRoutine, bRoutine}) {
                if (routine != null && routine.getId() != null
                        && DB.routines().findById(routine.getId()) != null) {
                    DB.routines().remove(routine);
                }
            }
            for (Patient patient : new Patient[]{first, second}) {
                if (patient.getId() != null && DB.patients().findById(patient.getId()) != null) {
                    DB.patients().remove(patient);
                }
            }
        }
    }
}
