/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities.schedules;

import android.os.Bundle;
import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.persistence.Patient;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Synthetic, nonclinical owner codes only; no real patient records. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ScheduleEditorOwnerStateTest {
    private static Patient patient(long id, String code) {
        Patient p = new Patient();
        p.setId(id);
        p.setCode(code);
        return p;
    }

    @Test
    public void validRestorePreservesOriginalOwnerAcrossBundleParcel() {
        Patient opened = patient(920000100L, "synthetic-original-owner");
        ScheduleEditorOwnerState.OwnerBinding original =
                ScheduleEditorOwnerState.restoredOwnerOrNull(null, opened);
        assertNotNull(original);
        Bundle state = new Bundle();
        ScheduleEditorOwnerState.saveBinding(state, original);

        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeBundle(state);
            parcel.setDataPosition(0);
            Bundle roundTrip = parcel.readBundle(getClass().getClassLoader());
            ScheduleEditorOwnerState.OwnerBinding restored =
                    ScheduleEditorOwnerState.restoredOwnerOrNull(roundTrip,
                            patient(920000100L, "synthetic-original-owner"));
            assertNotNull(restored);
            assertTrue(ScheduleEditorOwnerState.stillOwnedBy(restored, opened));
            assertFalse("Only a digest may be stored in the UI Bundle",
                    roundTrip.toString().contains("synthetic-original-owner"));
        } finally {
            parcel.recycle();
        }
    }

    @Test
    public void recycledIdOrMissingStableIdentityCannotInheritRestoredEditor() {
        Patient original = patient(920000200L, "synthetic-before-backup");
        ScheduleEditorOwnerState.OwnerBinding binding =
                ScheduleEditorOwnerState.restoredOwnerOrNull(null, original);
        Bundle state = new Bundle();
        ScheduleEditorOwnerState.saveBinding(state, binding);

        assertNull(ScheduleEditorOwnerState.restoredOwnerOrNull(state,
                patient(920000200L, "synthetic-after-backup")));
        assertNull(ScheduleEditorOwnerState.restoredOwnerOrNull(state,
                patient(920000200L, null)));
        assertNull(ScheduleEditorOwnerState.restoredOwnerOrNull(state,
                patient(920000200L, " ")));
        assertNull(ScheduleEditorOwnerState.restoredOwnerOrNull(state,
                patient(920000201L, "synthetic-before-backup")));
        assertFalse(ScheduleEditorOwnerState.stillOwnedBy(binding,
                patient(920000200L, "synthetic-after-backup")));
    }

    @Test
    public void untrustedOrLegacyRestorationStateFailsClosed() {
        Patient opened = patient(920000300L, "synthetic-owner");
        assertNull(ScheduleEditorOwnerState.restoredOwnerOrNull(new Bundle(), opened));
        Bundle oldIdOnly = new Bundle();
        oldIdOnly.putLong("schedule_editor_original_patient_id", opened.getId());
        assertNull(ScheduleEditorOwnerState.restoredOwnerOrNull(oldIdOnly, opened));
        Bundle wrongType = new Bundle();
        wrongType.putString("schedule_editor_original_patient_id",
                String.valueOf(opened.getId()));
        wrongType.putString("schedule_editor_original_patient_fingerprint",
                "a".repeat(64));
        assertNull(ScheduleEditorOwnerState.restoredOwnerOrNull(wrongType, opened));
        assertNull(ScheduleEditorOwnerState.restoredOwnerOrNull(null,
                patient(920000300L, null)));
    }
}
