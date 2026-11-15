/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

import android.os.Bundle;

import es.usc.citius.servando.calendula.persistence.Patient;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** No real patient data. Routine creation must survive only the same owner. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class RoutineEditorOwnerStateTest {
    private static Patient patient(long id) {
        Patient p = new Patient();
        p.setId(id);
        p.setCode("ci-routine-editor-" + id);
        return p;
    }

    private static RoutineEditorOwnerState.OwnerBinding open(Patient patient) {
        return RoutineEditorOwnerState.restoredOwnerOrNull(null, patient);
    }

    @Test
    public void freshRoutineEditorUsesValidPersistedPatientIdentity() {
        RoutineEditorOwnerState.OwnerBinding current = open(patient(101L));
        assertNotNull(current);
        assertEquals(Long.valueOf(101L), current.id);
        assertTrue(RoutineEditorOwnerState.stillOwnedBy(current, patient(101L)));
        assertNull(open(null));
        assertNull(open(new Patient()));
        assertNull(open(patient(0L)));
        Patient unidentified = patient(101L);
        unidentified.setCode(null);
        assertNull(open(unidentified));
        assertFalse(RoutineEditorOwnerState.stillOwnedBy(current, unidentified));
    }

    @Test
    public void previousIdOnlyOrMalformedSavedStateFailsClosed() {
        Patient active = patient(101L);
        assertNull(RoutineEditorOwnerState.restoredOwnerOrNull(new Bundle(), active));
        Bundle old = new Bundle();
        old.putLong(RoutineEditorOwnerState.ORIGINAL_OWNER, 101L);
        assertNull("A pre-migration numeric-only state may not be rebound",
                RoutineEditorOwnerState.restoredOwnerOrNull(old, active));
        old.putString(RoutineEditorOwnerState.ORIGINAL_OWNER_FINGERPRINT, "invalid");
        assertNull(RoutineEditorOwnerState.restoredOwnerOrNull(old, active));
        old.putString(RoutineEditorOwnerState.ORIGINAL_OWNER, "101");
        assertNull(RoutineEditorOwnerState.restoredOwnerOrNull(old, active));
        old.putInt(RoutineEditorOwnerState.ORIGINAL_OWNER, 101);
        assertNull(RoutineEditorOwnerState.restoredOwnerOrNull(old, active));
    }

    @Test
    public void savedDigestIdentifiesExactlyTheOriginalPatientAfterRotationAndRestore() {
        Patient original = patient(101L);
        RoutineEditorOwnerState.OwnerBinding opened = open(original);
        Bundle saved = new Bundle();
        RoutineEditorOwnerState.saveBinding(saved, opened);
        assertEquals(101L, saved.getLong(RoutineEditorOwnerState.ORIGINAL_OWNER));
        String fingerprint = saved.getString(
                RoutineEditorOwnerState.ORIGINAL_OWNER_FINGERPRINT);
        assertNotNull(fingerprint);
        assertTrue(fingerprint.matches("[0-9a-f]{64}"));
        assertFalse("Never persist the provider subject in plaintext",
                fingerprint.contains(original.getCode()));

        RoutineEditorOwnerState.OwnerBinding restored =
                RoutineEditorOwnerState.restoredOwnerOrNull(saved, patient(101L));
        assertNotNull(restored);
        assertTrue(RoutineEditorOwnerState.stillOwnedBy(restored, original));
        assertFalse(RoutineEditorOwnerState.stillOwnedBy(restored, patient(102L)));
        assertNull(RoutineEditorOwnerState.restoredOwnerOrNull(saved, patient(102L)));

        Patient recycled = patient(101L);
        recycled.setCode("ci-completely-different-owner");
        assertNull("Recycled SQLite ID cannot adopt the old routine draft",
                RoutineEditorOwnerState.restoredOwnerOrNull(saved, recycled));
        assertFalse(RoutineEditorOwnerState.stillOwnedBy(opened, recycled));
        recycled.setCode(null);
        assertNull(RoutineEditorOwnerState.restoredOwnerOrNull(saved, recycled));
        assertFalse(RoutineEditorOwnerState.stillOwnedBy(opened, recycled));

        saved.putString(RoutineEditorOwnerState.ORIGINAL_OWNER_FINGERPRINT,
                "0".repeat(64));
        assertNull(RoutineEditorOwnerState.restoredOwnerOrNull(saved, original));
        saved.putLong(RoutineEditorOwnerState.ORIGINAL_OWNER, -1L);
        assertNull(RoutineEditorOwnerState.restoredOwnerOrNull(saved, original));
    }

    @Test
    @Config(sdk = 33)
    public void wrongTypeCannotAuthorizeRoutineOnAndroid13() {
        Bundle saved = new Bundle();
        saved.putInt(RoutineEditorOwnerState.ORIGINAL_OWNER, 101);
        saved.putInt(RoutineEditorOwnerState.ORIGINAL_OWNER_FINGERPRINT, 7);
        assertNull(RoutineEditorOwnerState.restoredOwnerOrNull(saved, patient(101L)));
    }
}
