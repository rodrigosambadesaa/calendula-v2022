/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** No Android/SQLite access: authorization must be tested deterministically. */
public class MedicineDetailsOwnerGuardTest {
    @Test
    public void permitsOnlyPersistedMedicineOwnedByOpeningAndActivePatient() {
        assertTrue(MedicineDetailsOwnerGuard.matches(11L, 11L, 22L, 11L, 22L, 11L));
        assertFalse(MedicineDetailsOwnerGuard.matches(11L, 12L, 22L, 11L, 22L, 11L));
        assertFalse(MedicineDetailsOwnerGuard.matches(11L, 11L, 22L, 12L, 22L, 11L));
        assertFalse(MedicineDetailsOwnerGuard.matches(11L, 11L, 22L, 11L, 22L, 12L));
        assertFalse(MedicineDetailsOwnerGuard.matches(11L, 11L, 22L, 11L, 23L, 11L));
    }

    @Test
    public void refusesDeletedMissingAndUnsavedIds() {
        assertFalse(MedicineDetailsOwnerGuard.matches(null, 11L, 22L, 11L, 22L, 11L));
        assertFalse(MedicineDetailsOwnerGuard.matches(11L, null, 22L, 11L, 22L, 11L));
        assertFalse(MedicineDetailsOwnerGuard.matches(11L, 11L, null, 11L, 22L, 11L));
        assertFalse(MedicineDetailsOwnerGuard.matches(11L, 11L, 22L, 11L, null, 11L));
        assertFalse(MedicineDetailsOwnerGuard.matches(0L, 0L, 22L, 0L, 22L, 0L));
        assertFalse(MedicineDetailsOwnerGuard.matches(11L, 11L, -1L, 11L, -1L, 11L));
    }
    @Test
    public void modelEventsRequireSameStablePatientAndExistingMedicine() {
        assertTrue(MedicineDetailsOwnerGuard.sameMedicineUpdate(
                22L, 11L, "synthetic-owner", 22L, 11L, "synthetic-owner"));
        assertFalse(MedicineDetailsOwnerGuard.sameMedicineUpdate(
                22L, 11L, "synthetic-owner", 22L, 11L, "reused-owner"));
        assertFalse(MedicineDetailsOwnerGuard.sameMedicineUpdate(
                22L, 11L, "synthetic-owner", 22L, 12L, "synthetic-owner"));
        assertFalse(MedicineDetailsOwnerGuard.sameMedicineUpdate(
                22L, 11L, "synthetic-owner", 23L, 11L, "synthetic-owner"));
        assertFalse(MedicineDetailsOwnerGuard.sameMedicineUpdate(
                null, 11L, "synthetic-owner", 22L, 11L, "synthetic-owner"));
        assertFalse(MedicineDetailsOwnerGuard.sameMedicineUpdate(
                22L, null, "synthetic-owner", 22L, 11L, "synthetic-owner"));
        assertFalse(MedicineDetailsOwnerGuard.sameMedicineUpdate(
                22L, 11L, null, 22L, 11L, "synthetic-owner"));
        assertFalse(MedicineDetailsOwnerGuard.sameMedicineUpdate(
                22L, 11L, " ", 22L, 11L, " "));
        assertFalse(MedicineDetailsOwnerGuard.sameMedicineUpdate(
                22L, 11L, "synthetic-owner", 22L, 11L, null));
    }

}
