/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Background search results must retain the patient selected at launch. */
public class MedicineSearchOwnerGuardTest {
    @Test
    public void samePatientPermitsResultsAfterNormalRecreation() {
        assertTrue(MedicineSearchOwnerGuard.samePatient(170L, 170L));
    }

    @Test
    public void changedOrMissingPatientAlwaysRejectsResults() {
        assertFalse(MedicineSearchOwnerGuard.samePatient(170L, 171L));
        assertFalse(MedicineSearchOwnerGuard.samePatient(null, 170L));
        assertFalse(MedicineSearchOwnerGuard.samePatient(170L, null));
        assertFalse(MedicineSearchOwnerGuard.samePatient(0L, 0L));
        assertFalse(MedicineSearchOwnerGuard.samePatient(-1L, -1L));
    }
}
