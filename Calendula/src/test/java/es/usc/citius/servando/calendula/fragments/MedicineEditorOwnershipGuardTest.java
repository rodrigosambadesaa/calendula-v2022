/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

/** JUnit policy regressions independent of Android, ORM and patients' data. */
public class MedicineEditorOwnershipGuardTest {
    @Test
    public void creationUsesOnlyThePatientPresentWhenEditorOpened() {
        assertTrue(MedicineEditorOwnershipGuard.sameEditorPatient(31L, 31L));
        assertFalse(MedicineEditorOwnershipGuard.sameEditorPatient(31L, 32L));
        assertFalse(MedicineEditorOwnershipGuard.sameEditorPatient(null, 31L));
        assertFalse(MedicineEditorOwnershipGuard.sameEditorPatient(0L, 0L));
        assertFalse(MedicineEditorOwnershipGuard.sameEditorPatient(31L, null));
    }

    @Test
    public void editingRequiresPersistedMedicineAndOriginalOwner() {
        assertTrue(MedicineEditorOwnershipGuard.canEdit(31L, 31L,
                44L, 31L, 44L, 31L));
        assertFalse(MedicineEditorOwnershipGuard.canEdit(31L, 32L,
                44L, 31L, 44L, 31L));
        assertFalse(MedicineEditorOwnershipGuard.canEdit(31L, 31L,
                44L, 31L, 44L, 32L));
        assertFalse(MedicineEditorOwnershipGuard.canEdit(31L, 31L,
                44L, 31L, 45L, 31L));
        assertFalse(MedicineEditorOwnershipGuard.canEdit(31L, 31L,
                44L, 32L, 44L, 31L));
        assertFalse(MedicineEditorOwnershipGuard.canEdit(31L, 31L,
                null, 31L, 44L, 31L));
    }
}
