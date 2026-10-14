/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Pure tests for profile switches while an allergy confirmation is open. */
public class MedicineSaveOwnerGuardTest {
    @Test
    public void foreignMedicineIntentIsRejectedBeforeEditorDisplaysData() {
        assertTrue(MedicineSaveOwnerGuard.canOpenForEdit(
                17L, 17L, 28L, 28L, 17L));
        assertFalse(MedicineSaveOwnerGuard.canOpenForEdit(
                17L, 17L, 28L, 28L, 18L)); // Existing med belongs to B
        assertFalse(MedicineSaveOwnerGuard.canOpenForEdit(
                17L, 18L, 28L, 28L, 17L)); // Profile switched
        assertFalse(MedicineSaveOwnerGuard.canOpenForEdit(
                17L, 17L, 28L, 29L, 17L)); // Stale medicine identity
        assertFalse(MedicineSaveOwnerGuard.canOpenForEdit(
                17L, 17L, 28L, null, 17L)); // Deleted medicine
        assertFalse(MedicineSaveOwnerGuard.canOpenForEdit(
                17L, 17L, null, 28L, 17L));
        assertFalse(MedicineSaveOwnerGuard.canOpenForEdit(
                17L, 17L, 0L, 0L, 17L));
    }

    @Test
    public void onlyOriginalActiveProfileCanCreate() {
        assertTrue(MedicineSaveOwnerGuard.canCreate(13L, 13L, 13L, null));
        assertFalse(MedicineSaveOwnerGuard.canCreate(13L, 14L, 13L, null));
        assertFalse(MedicineSaveOwnerGuard.canCreate(13L, 13L, 14L, null));
        assertFalse(MedicineSaveOwnerGuard.canCreate(13L, 13L, 13L, 22L));
        assertFalse(MedicineSaveOwnerGuard.canCreate(null, 13L, 13L, null));
    }

    @Test
    public void delayedEditOrDeletionRequiresPersistedOriginalOwner() {
        assertTrue(MedicineSaveOwnerGuard.canModify(
                13L, 13L, 13L, 22L, 13L, 22L));
        assertFalse(MedicineSaveOwnerGuard.canModify(
                13L, 14L, 13L, 22L, 13L, 22L));
        assertFalse(MedicineSaveOwnerGuard.canModify(
                13L, 13L, 14L, 22L, 13L, 22L));
        assertFalse(MedicineSaveOwnerGuard.canModify(
                13L, 13L, 13L, 22L, 14L, 22L));
        assertFalse(MedicineSaveOwnerGuard.canModify(
                13L, 13L, 13L, 22L, 13L, 23L));
        assertFalse(MedicineSaveOwnerGuard.canModify(
                13L, 13L, 13L, null, 13L, null));
    }
}
