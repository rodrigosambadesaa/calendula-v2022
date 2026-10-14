/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import org.junit.Test;
import static org.junit.Assert.*;

/** Background results may be applied only to their original visible query. */
public class AllergenSearchRequestGuardTest {
    @Test
    public void onlyLatestMatchingSearchMayPopulateAllergenList() {
        assertTrue(AllergenSearchRequestGuard.isCurrent(7, 7, "lactosa", "lactosa"));
        assertFalse(AllergenSearchRequestGuard.isCurrent(6, 7, "lactosa", "lactosa"));
        assertFalse(AllergenSearchRequestGuard.isCurrent(7, 7, "lactosa", "gluten"));
        assertFalse(AllergenSearchRequestGuard.isCurrent(7, 7, "lactosa", "lac"));
        assertFalse(AllergenSearchRequestGuard.isCurrent(7, 7, "lactosa", ""));
        assertFalse(AllergenSearchRequestGuard.isCurrent(7, 7, null, null));
        assertFalse(AllergenSearchRequestGuard.isCurrent(7, 7, "la", "la"));
    }
}
