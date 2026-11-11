/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

import android.os.Bundle;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.persistence.Patient;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Android Bundle/Parcel round-trip as after a process recreation; no real
 * patient records, external service, medication or SQLite changes.
 */
@RunWith(AndroidJUnit4.class)
public class RoutineEditorOwnerRestoreSmokeTest {
    private static Patient owner(long id, String code) {
        Patient patient = new Patient();
        patient.setId(id);
        patient.setCode(code);
        return patient;
    }

    @Test
    public void recreatedRoutineDraftCannotFollowRecycledPatientId() {
        Patient original = owner(8899101L, "ci-original-routine-owner");
        RoutineEditorOwnerState.OwnerBinding opened =
                RoutineEditorOwnerState.restoredOwnerOrNull(null, original);
        assertNotNull(opened);
        Bundle saved = new Bundle();
        RoutineEditorOwnerState.saveBinding(saved, opened);

        Parcel parcel = Parcel.obtain();
        Bundle restored;
        try {
            saved.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            restored = parcel.readBundle(getClass().getClassLoader());
        } finally {
            parcel.recycle();
        }
        assertNotNull(restored);
        RoutineEditorOwnerState.OwnerBinding accepted =
                RoutineEditorOwnerState.restoredOwnerOrNull(restored,
                        owner(8899101L, "ci-original-routine-owner"));
        assertNotNull(accepted);
        assertTrue(RoutineEditorOwnerState.stillOwnedBy(accepted, original));

        Patient replaced = owner(8899101L, "ci-replacement-routine-owner");
        assertNull("New owner with same SQLite ID must not inherit routine draft",
                RoutineEditorOwnerState.restoredOwnerOrNull(restored, replaced));
        assertFalse(RoutineEditorOwnerState.stillOwnedBy(accepted, replaced));
        Patient unidentified = owner(8899101L, null);
        assertNull(RoutineEditorOwnerState.restoredOwnerOrNull(restored, unidentified));

        // Old releases stored only a numeric ID, not the stable fingerprint.
        Bundle oldSaved = new Bundle();
        oldSaved.putLong(RoutineEditorOwnerState.ORIGINAL_OWNER, 8899101L);
        assertNull("ID-only legacy draft restoration must fail closed",
                RoutineEditorOwnerState.restoredOwnerOrNull(oldSaved, original));
    }
}
