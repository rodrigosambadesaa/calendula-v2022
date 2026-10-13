/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.login;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Routine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Synthetic SQLite bootstrap regression; no OAuth provider or real user. */
@RunWith(AndroidJUnit4.class)
public class LoginPatientBootstrapSmokeTest {
    @Test
    public void failedDefaultRoutineInsertCannotCommitAuthenticatedPatient() throws Exception {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String code = "ci-auth-bootstrap-rollback-" + System.nanoTime();
        Patient candidate = new Patient();
        candidate.setCode(code);
        candidate.setName("Synthetic OAuth bootstrap rollback");
        SQLiteDatabase sqlite = DB.helper().getWritableDatabase();
        String trigger = "ci_failed_login_initial_routine";
        sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
        try {
            sqlite.execSQL("CREATE TRIGGER " + trigger
                    + " BEFORE INSERT ON Routines"
                    + " BEGIN SELECT RAISE(ABORT, 'synthetic initial routine failure'); END;");
            boolean failed = false;
            try {
                LoginStateManager.createLocalPatientAndRoutines(candidate, context);
            } catch (RuntimeException expected) {
                failed = true;
            }
            assertTrue("Failed SQL routine insert must block profile creation", failed);
            assertNull("No partial patient row survives failed login bootstrap",
                    DB.patients().findOneBy(Patient.COLUMN_CODE, code));
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            // Retry with a fresh Java object: SQLite rolled back the previous
            // row, but ORMLite might retain a generated ID in the old object.
            Patient retry = new Patient();
            retry.setCode(code);
            retry.setName("Synthetic retry authenticated owner");
            LoginStateManager.createLocalPatientAndRoutines(retry, context);
            assertNotNull(retry.getId());
            assertNotNull(DB.patients().findById(retry.getId()));
            List<Routine> routines = DB.routines().findAll(retry.getId());
            assertTrue("Successful login creates every default routine",
                    routines.size() >= 3);
        } finally {
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            Patient stored = DB.patients().findOneBy(Patient.COLUMN_CODE, code);
            if (stored != null) {
                for (Routine routine : DB.routines().findAll(stored.getId())) {
                    DB.routines().remove(routine);
                }
                DB.patients().remove(stored);
            }
        }
    }
}
