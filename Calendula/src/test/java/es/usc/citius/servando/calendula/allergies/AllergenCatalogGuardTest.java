/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.allergies;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Deterministic catalog completeness checks, without accessing patient data. */
public class AllergenCatalogGuardTest {
    @Test
    public void onlyUsablePrescriptionCodesAuthorizeLookup() {
        assertTrue(AllergenCatalogGuard.validPrescriptionCode("1234"));
        assertFalse(AllergenCatalogGuard.validPrescriptionCode(null));
        assertFalse(AllergenCatalogGuard.validPrescriptionCode(""));
        assertFalse(AllergenCatalogGuard.validPrescriptionCode("  \t "));
    }

    @Test
    public void missingAndDuplicateCatalogRecordsAreNotNegativeAllergyResults() {
        assertFalse(AllergenCatalogGuard.uniqueCatalogMatch(0));
        assertTrue(AllergenCatalogGuard.uniqueCatalogMatch(1));
        assertFalse(AllergenCatalogGuard.uniqueCatalogMatch(2));
        assertFalse(AllergenCatalogGuard.uniqueCatalogMatch(-1));
    }
}
