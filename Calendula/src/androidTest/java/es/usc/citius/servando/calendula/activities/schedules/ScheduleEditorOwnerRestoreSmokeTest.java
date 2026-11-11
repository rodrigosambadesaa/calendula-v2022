/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities.schedules;

import android.os.Bundle;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.util.ScheduleSaveOwnerGuard;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Only disposable synthetic SQLite records are used. */
@RunWith(AndroidJUnit4.class)
public class ScheduleEditorOwnerRestoreSmokeTest {
    @Test
    public void restoredSqliteIdCannotAdoptPriorScheduleEditorOrQueuedSave()
            throws Exception {
        assertTrue(DB.initialized);
        String suffix = String.valueOf(System.nanoTime());
        String originalCode = "ci-schedule-original-" + suffix;
        String replacementCode = "ci-schedule-replaced-" + suffix;
        Patient original = new Patient();
        original.setCode(originalCode);
        original.setName("Synthetic editor owner");
        try {
            DB.patients().save(original);
            Patient stored = DB.patients().findById(original.getId());
            assertNotNull(stored);

            ScheduleEditorOwnerState.OwnerBinding binding =
                    ScheduleEditorOwnerState.restoredOwnerOrNull(null, stored);
            assertNotNull(binding);
            Bundle state = new Bundle();
            ScheduleEditorOwnerState.saveBinding(state, binding);
            Parcel parcel = Parcel.obtain();
            try {
                parcel.writeBundle(state);
                parcel.setDataPosition(0);
                Bundle restored = parcel.readBundle(getClass().getClassLoader());
                assertNotNull(ScheduleEditorOwnerState.restoredOwnerOrNull(restored, stored));

                // Simulate a backup/restore replacement retaining the same
                // SQLite row ID, without introducing any real patient data.
                DB.helper().getWritableDatabase().execSQL(
                        "UPDATE Patients SET Code = ? WHERE _id = ?",
                        new Object[]{replacementCode, original.getId()});
                Patient replacement = DB.patients().findById(original.getId());
                assertNotNull(replacement);
                assertTrue(replacementCode.equals(replacement.getCode()));
                assertNull(ScheduleEditorOwnerState.restoredOwnerOrNull(
                        restored, replacement));
                assertFalse(ScheduleEditorOwnerState.stillOwnedBy(binding, replacement));
                assertFalse(ScheduleSaveOwnerGuard.canShowEditorMedicationList(
                        original, replacement, replacement));
            } finally {
                parcel.recycle();
            }
        } finally {
            if (original.getId() != null) {
                DB.helper().getWritableDatabase().execSQL(
                        "UPDATE Patients SET Code = ? WHERE _id = ?",
                        new Object[]{originalCode, original.getId()});
                Patient cleanup = DB.patients().findById(original.getId());
                if (cleanup != null) {
                    DB.patients().remove(cleanup);
                }
            }
        }
    }
}
