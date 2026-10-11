/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.joda.time.LocalTime;
import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Routine;
import es.usc.citius.servando.calendula.util.RoutineOwnerGuard;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Verify owner identity against SQLite records on actual Android emulators.
 * Synthetic patients and routines are always removed in finally.
 */
@RunWith(AndroidJUnit4.class)
public class RoutineEditorOwnershipSmokeTest {

    @Test
    public void editorMayAccessOnlyRoutinesOfItsPersistedPatient() {
        assertTrue(DB.initialized);
        String seed = String.valueOf(System.nanoTime());
        Patient a = new Patient();
        a.setCode("ci-routine-owner-a-" + seed);
        a.setName("Synthetic routine patient A");
        Patient b = new Patient();
        b.setCode("ci-routine-owner-b-" + seed);
        b.setName("Synthetic routine patient B");
        Routine routineA = new Routine(a, new LocalTime(8, 13), "Synthetic routine A " + seed);
        Routine routineB = new Routine(b, new LocalTime(18, 27), "Synthetic routine B " + seed);
        try {
            DB.patients().save(a);
            DB.patients().save(b);
            DB.routines().save(routineA);
            DB.routines().save(routineB);
            Routine persistedA = DB.routines().findById(routineA.getId());
            Routine persistedB = DB.routines().findById(routineB.getId());
            assertNotNull(persistedA);
            assertNotNull(persistedB);

            assertTrue(RoutineOwnerGuard.isOwnedBy(persistedA, a));
            assertTrue(RoutineOwnerGuard.isOwnedBy(persistedB, b));
            assertFalse("A cannot open/delete B's routine",
                    RoutineOwnerGuard.isOwnedBy(persistedB, a));
            assertFalse("B cannot open/delete A's routine",
                    RoutineOwnerGuard.isOwnedBy(persistedA, b));
            assertFalse(RoutineOwnerGuard.isOwnedBy(persistedA, new Patient()));
            // No writes are performed during the authorization checks.
            assertNotNull(DB.routines().findById(routineA.getId()));
            assertNotNull(DB.routines().findById(routineB.getId()));
        } finally {
            for (Routine routine : new Routine[]{routineA, routineB}) {
                if (routine.getId() != null && DB.routines().findById(routine.getId()) != null) {
                    DB.routines().remove(routine);
                }
            }
            for (Patient p : new Patient[]{a, b}) {
                if (p.getId() != null && DB.patients().findById(p.getId()) != null) {
                    DB.patients().remove(p);
                }
            }
        }
    }
}
