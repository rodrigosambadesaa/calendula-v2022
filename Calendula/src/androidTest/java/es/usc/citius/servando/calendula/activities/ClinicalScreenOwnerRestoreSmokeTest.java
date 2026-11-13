/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import android.os.Bundle;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.util.ClinicalScreenOwnerBinding;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Real SQLite and Bundle/Parcel only; all records are disposable. */
@RunWith(AndroidJUnit4.class)
public class ClinicalScreenOwnerRestoreSmokeTest {
    @Test
    public void oldMedicineEditorAndDetailsCannotAdoptRestoredPatientRow()
            throws Exception {
        assertTrue(DB.initialized);
        String seed = String.valueOf(System.nanoTime());
        String initialCode = "ci-clinical-owner-original-" + seed;
        String successorCode = "ci-clinical-owner-reused-" + seed;
        Patient owner = new Patient();
        owner.setCode(initialCode);
        owner.setName("Synthetic original clinical owner");
        try {
            DB.patients().save(owner);
            Patient original = DB.patients().findById(owner.getId());
            assertNotNull(original);
            ClinicalScreenOwnerBinding editor =
                    ClinicalScreenOwnerBinding.restoreOrCapture(
                            null, original, "medicine-editor");
            ClinicalScreenOwnerBinding details =
                    ClinicalScreenOwnerBinding.restoreOrCapture(
                            null, original, "medicine-info");
            assertNotNull(editor);
            assertNotNull(details);

            Bundle editorBundle = new Bundle();
            Bundle detailBundle = new Bundle();
            editor.save(editorBundle);
            details.save(detailBundle);
            Parcel parcel = Parcel.obtain();
            try {
                parcel.writeBundle(editorBundle);
                parcel.setDataPosition(0);
                Bundle restoredEditor = parcel.readBundle(getClass().getClassLoader());
                assertNotNull(ClinicalScreenOwnerBinding.restoreOrCapture(
                        restoredEditor, original, "medicine-editor"));

                // Model a restored owner at the same SQLite ID, without any
                // real medication data or patient information.
                DB.helper().getWritableDatabase().execSQL(
                        "UPDATE Patients SET Code = ? WHERE _id = ?",
                        new Object[]{successorCode, owner.getId()});
                Patient successor = DB.patients().findById(owner.getId());
                assertNotNull(successor);
                assertTrue(successorCode.equals(successor.getCode()));
                assertFalse(editor.matches(successor));
                assertFalse(details.matches(successor));
                assertNull(ClinicalScreenOwnerBinding.restoreOrCapture(
                        restoredEditor, successor, "medicine-editor"));
                assertNull(ClinicalScreenOwnerBinding.restoreOrCapture(
                        detailBundle, successor, "medicine-info"));
                assertNull(ClinicalScreenOwnerBinding.restoreOrCapture(
                        restoredEditor, successor, "medicine-info"));
            } finally {
                parcel.recycle();
            }
        } finally {
            if (owner.getId() != null) {
                DB.helper().getWritableDatabase().execSQL(
                        "UPDATE Patients SET Code = ? WHERE _id = ?",
                        new Object[]{initialCode, owner.getId()});
                Patient cleanup = DB.patients().findById(owner.getId());
                if (cleanup != null) {
                    DB.patients().remove(cleanup);
                }
            }
        }
    }
}
