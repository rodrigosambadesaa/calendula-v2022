/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Routine;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

/** No real patient records are required to verify edit ownership. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class RoutineOwnerGuardTest {

    private Patient patient(long id) {
        Patient p = new Patient();
        p.setId(id);
        return p;
    }

    private Routine routine(Long id, Patient owner) {
        Routine r = new Routine();
        r.setId(id);
        r.setPatient(owner);
        return r;
    }

    @Test
    public void samePersistedOwnerCanEditAfterSqliteReload() {
        Patient active = patient(600123L);
        Patient reloaded = patient(600123L);
        assertNotSame(active.getId(), reloaded.getId());
        assertTrue(RoutineOwnerGuard.isOwnedBy(
                routine(800345L, reloaded), active));
    }

    @Test
    public void cannotViewOrChangeAnotherPatientRoutine() {
        Patient active = patient(600123L);
        assertFalse(RoutineOwnerGuard.isOwnedBy(
                routine(800345L, patient(600124L)), active));
        assertFalse(RoutineOwnerGuard.isOwnedBy(null, active));
        assertFalse(RoutineOwnerGuard.isOwnedBy(
                routine(800345L, active), patient(600124L)));
    }

    @Test
    public void missingOrUnsavedIdentifiersMustFailClosed() {
        Patient active = patient(600123L);
        Routine saved = routine(800345L, active);
        assertFalse(RoutineOwnerGuard.hasActivePatient(null));
        assertFalse(RoutineOwnerGuard.hasActivePatient(new Patient()));
        assertFalse(RoutineOwnerGuard.isOwnedBy(saved, null));
        assertFalse(RoutineOwnerGuard.isOwnedBy(saved, new Patient()));
        assertFalse(RoutineOwnerGuard.isOwnedBy(
                routine(null, active), active));
        assertFalse(RoutineOwnerGuard.isOwnedBy(
                routine(800345L, new Patient()), active));
        assertFalse(RoutineOwnerGuard.isOwnedBy(
                routine(800345L, null), active));
        assertFalse(RoutineOwnerGuard.isOwnedBy(
                routine(0L, active), active));
    }
}
