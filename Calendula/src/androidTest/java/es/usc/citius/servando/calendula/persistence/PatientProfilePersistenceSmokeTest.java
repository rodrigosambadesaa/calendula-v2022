/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.persistence;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import es.usc.citius.servando.calendula.database.DB;
import static org.junit.Assert.*;

/** Synthetic SQLite test for manual patient creation/edits. */
@RunWith(AndroidJUnit4.class)
public class PatientProfilePersistenceSmokeTest {
    @Test
    public void failedOptionalMealInsertionRollsBackPatientAndAllowsSameEditorRetry()
            throws Exception {
        assertTrue(DB.initialized);
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String code = "ci-manual-profile-rollback-" + System.nanoTime();
        Patient draft = new Patient();
        draft.setCode(code);
        draft.setName("Synthetic manual profile");
        SQLiteDatabase sqlite = DB.helper().getWritableDatabase();
        final String trigger = "ci_manual_profile_meal_abort";
        sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
        try {
            sqlite.execSQL("CREATE TRIGGER " + trigger
                    + " BEFORE INSERT ON Routines"
                    + " BEGIN SELECT RAISE(ABORT, 'synthetic default meal insert failure'); END;");
            boolean rejected = false;
            try {
                PatientProfilePersistence.save(draft, ctx, true);
            } catch (RuntimeException expected) {
                rejected = true;
            }
            assertTrue("A failed default meal must reject the whole profile save", rejected);
            assertNull("No half-created patient must persist",
                    DB.patients().findOneBy(Patient.COLUMN_CODE, code));
            assertNull("Failed UI draft must remain insertable on retry", draft.getId());

            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            PatientProfilePersistence.save(draft, ctx, true);
            assertNotNull(draft.getId());
            assertNotNull(DB.patients().findById(draft.getId()));
            assertEquals(3, DB.routines().findAll(draft.getId()).size());

            draft.setName("Synthetic manual profile edited");
            PatientProfilePersistence.save(draft, ctx, false);
            assertEquals("Synthetic manual profile edited",
                    DB.patients().findById(draft.getId()).getName());
            assertEquals("Editing must not duplicate default meal routines", 3,
                    DB.routines().findAll(draft.getId()).size());
        } finally {
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            Patient stored = DB.patients().findOneBy(Patient.COLUMN_CODE, code);
            if (stored != null) {
                for (Routine r : DB.routines().findAll(stored.getId())) {
                    DB.routines().remove(r);
                }
                DB.patients().remove(stored);
            }
        }
    }
}
