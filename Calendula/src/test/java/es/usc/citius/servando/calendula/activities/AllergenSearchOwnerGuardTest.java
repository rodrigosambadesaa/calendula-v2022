/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

/** A search result must never be applied to a different clinical profile. */
public class AllergenSearchOwnerGuardTest {
    @Test
    public void onlyOriginalPatientMayReceiveSearchResults() {
        assertTrue(AllergenSearchOwnerGuard.samePatient(11L, 11L));
        assertFalse(AllergenSearchOwnerGuard.samePatient(11L, 12L));
        assertFalse(AllergenSearchOwnerGuard.samePatient(null, 11L));
        assertFalse(AllergenSearchOwnerGuard.samePatient(11L, null));
        assertFalse(AllergenSearchOwnerGuard.samePatient(0L, 0L));
        assertFalse(AllergenSearchOwnerGuard.samePatient(-1L, -1L));
    }

    @Test
    public void insertedAllergenMustBelongToOriginalActivePatient() {
        assertTrue(AllergenSearchOwnerGuard.canStore(11L, 11L, 11L));
        assertFalse(AllergenSearchOwnerGuard.canStore(11L, 12L, 11L));
        assertFalse(AllergenSearchOwnerGuard.canStore(11L, 11L, 12L));
        assertFalse(AllergenSearchOwnerGuard.canStore(11L, 11L, null));
        assertFalse(AllergenSearchOwnerGuard.canStore(0L, 0L, 0L));
    }
}
