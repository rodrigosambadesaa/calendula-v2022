/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Request cancellation and patient isolation without an Android emulator. */
public class ActiveMedicationOwnerGuardTest {
    @Test
    public void onlyCurrentRequestForCurrentPatientCanPopulateTheList() {
        assertTrue(ActiveMedicationOwnerGuard.canApply(5L, 5L, 200L, 200L));
        assertFalse(ActiveMedicationOwnerGuard.canApply(4L, 5L, 200L, 200L));
        assertFalse(ActiveMedicationOwnerGuard.canApply(5L, 5L, 200L, 201L));
        assertFalse(ActiveMedicationOwnerGuard.canApply(5L, 5L, null, 200L));
        assertFalse(ActiveMedicationOwnerGuard.canApply(5L, 5L, -1L, -1L));
    }

    @Test
    public void noCardCanBeOpenedForANonActiveOrDeletedPatient() {
        assertTrue(ActiveMedicationOwnerGuard.matchingPatient(200L, 200L, 200L));
        assertFalse(ActiveMedicationOwnerGuard.matchingPatient(200L, 201L, 200L));
        assertFalse(ActiveMedicationOwnerGuard.matchingPatient(200L, 200L, 201L));
        assertFalse(ActiveMedicationOwnerGuard.matchingPatient(null, 200L, 200L));
        assertFalse(ActiveMedicationOwnerGuard.matchingPatient(200L, null, 200L));
        assertFalse(ActiveMedicationOwnerGuard.matchingPatient(0L, 0L, 0L));
    }
}
