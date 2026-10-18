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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Routine creation must not silently follow a changed active patient. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class RoutineEditorOwnerStateTest {
    private static Patient patient(long id) {
        Patient p = new Patient();
        p.setId(id);
        return p;
    }

    @Test
    public void freshRoutineEditorUsesValidActivePatient() {
        assertEquals(Long.valueOf(101L),
                RoutineEditorOwnerState.restoredOwnerOrNull(null, patient(101L)));
        assertNull(RoutineEditorOwnerState.restoredOwnerOrNull(null, null));
        assertNull(RoutineEditorOwnerState.restoredOwnerOrNull(null, new Patient()));
        assertNull(RoutineEditorOwnerState.restoredOwnerOrNull(null, patient(0L)));
    }

    @Test
    public void missingOrWrongTypeRestoredPatientFailsClosed() {
        assertNull(RoutineEditorOwnerState.restoredOwnerOrNull(new Bundle(), patient(101L)));
        Bundle wrong = new Bundle();
        wrong.putString(RoutineEditorOwnerState.ORIGINAL_OWNER, "101");
        assertNull(RoutineEditorOwnerState.restoredOwnerOrNull(wrong, patient(101L)));
        wrong.putInt(RoutineEditorOwnerState.ORIGINAL_OWNER, 101);
        assertNull(RoutineEditorOwnerState.restoredOwnerOrNull(wrong, patient(101L)));
    }

    @Test
    public void cannotCreateRoutineUnderDifferentPatientAfterRestoration() {
        Bundle saved = new Bundle();
        saved.putLong(RoutineEditorOwnerState.ORIGINAL_OWNER, 101L);
        assertEquals(Long.valueOf(101L),
                RoutineEditorOwnerState.restoredOwnerOrNull(saved, patient(101L)));
        assertNull(RoutineEditorOwnerState.restoredOwnerOrNull(saved, patient(102L)));
        assertTrue(RoutineEditorOwnerState.stillOwnedBy(101L, patient(101L)));
        assertFalse(RoutineEditorOwnerState.stillOwnedBy(101L, patient(102L)));
        assertFalse(RoutineEditorOwnerState.stillOwnedBy(null, patient(101L)));
        assertFalse(RoutineEditorOwnerState.stillOwnedBy(101L, null));
        saved.putLong(RoutineEditorOwnerState.ORIGINAL_OWNER, -1L);
        assertNull(RoutineEditorOwnerState.restoredOwnerOrNull(saved, patient(101L)));
    }

    @Test
    @Config(sdk = 33)
    public void wrongTypeCannotAuthorizeRoutineOnAndroid13() {
        Bundle saved = new Bundle();
        saved.putInt(RoutineEditorOwnerState.ORIGINAL_OWNER, 101);
        assertNull(RoutineEditorOwnerState.restoredOwnerOrNull(saved, patient(101L)));
    }
}
