/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

/** Pure patient isolation checks without Android UI or SQLite. */
public class MedicineListPatientGuardTest {
    @Test
    public void onlyNewestReloadForCurrentPatientCanUpdateCards() {
        assertTrue(MedicineListPatientGuard.currentReload(8L, 8L, 22L, 22L));
        assertFalse(MedicineListPatientGuard.currentReload(8L, 9L, 22L, 22L));
        assertFalse(MedicineListPatientGuard.currentReload(8L, 8L, 22L, 23L));
        assertFalse(MedicineListPatientGuard.currentReload(8L, 8L, null, null));
        assertFalse(MedicineListPatientGuard.currentReload(8L, 8L, 0L, 0L));
    }

    @Test
    public void deletingOrOpeningMedicineRequiresOriginalAndPersistedOwner() {
        assertTrue(MedicineListPatientGuard.selectedMedicineIsOwned(
                6L, 22L, 22L, 6L, 22L));
        assertFalse(MedicineListPatientGuard.selectedMedicineIsOwned(
                6L, 22L, 23L, 6L, 22L));
        assertFalse(MedicineListPatientGuard.selectedMedicineIsOwned(
                6L, 22L, 22L, 6L, 23L));
        assertFalse(MedicineListPatientGuard.selectedMedicineIsOwned(
                6L, 22L, 22L, 7L, 22L));
        assertFalse(MedicineListPatientGuard.selectedMedicineIsOwned(
                null, 22L, 22L, null, 22L));
        assertFalse(MedicineListPatientGuard.selectedMedicineIsOwned(
                0L, 22L, 22L, 0L, 22L));
    }
}
