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
