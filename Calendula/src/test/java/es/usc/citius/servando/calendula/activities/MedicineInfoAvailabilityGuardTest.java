/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Pure navigation tests for removed or unavailable healthcare-provider rows. */
public class MedicineInfoAvailabilityGuardTest {
    @Test
    public void missingProviderCannotBeUsedByProviderOnlyIntent() {
        assertTrue(MedicineInfoAvailabilityGuard.canShowRequestedProvider(true, true));
        assertFalse(MedicineInfoAvailabilityGuard.canShowRequestedProvider(true, false));
        assertTrue(MedicineInfoAvailabilityGuard.canShowRequestedProvider(false, false));
    }

    @Test
    public void missingProviderKeepsLocalMedicineVisibleWithoutExternalTab() {
        assertTrue(MedicineInfoAvailabilityGuard.showLinkedProviderTab(true, true));
        assertFalse(MedicineInfoAvailabilityGuard.showLinkedProviderTab(true, false));
        assertFalse(MedicineInfoAvailabilityGuard.showLinkedProviderTab(false, true));
        assertFalse(MedicineInfoAvailabilityGuard.showLinkedProviderTab(false, false));
    }
}
