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
    public void newManualProfileGetsStableCodeAndStaleEditorCannotOverwriteIt()
            throws Exception {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Patient draft = new Patient();
        draft.setName("Synthetic manual offline profile");
        try {
            PatientProfilePersistence.save(draft, context, false);
            assertNotNull(draft.getId());
            assertNotNull(draft.getCode());
            assertTrue(draft.getCode().startsWith("local-"));
            assertEquals(draft.getCode(), DB.patients().findById(draft.getId()).getCode());

            draft.setName("Synthetic manual profile edited");
            PatientProfilePersistence.save(draft, context, false);
            assertEquals("Synthetic manual profile edited",
                    DB.patients().findById(draft.getId()).getName());

            Patient stale = new Patient();
            stale.setId(draft.getId());
            stale.setCode("synthetic-deleted-owner");
            stale.setName("Another patient's malicious replacement");
            boolean rejected = false;
            try {
                PatientProfilePersistence.save(stale, context, false);
            } catch (RuntimeException expected) {
                rejected = true;
            }
            assertTrue("Editor with reused row ID cannot overwrite another profile", rejected);
            assertEquals("Synthetic manual profile edited",
                    DB.patients().findById(draft.getId()).getName());
            assertEquals(draft.getCode(), DB.patients().findById(draft.getId()).getCode());
        } finally {
            if (draft.getId() != null && DB.patients().findById(draft.getId()) != null) {
                DB.patients().remove(draft);
            }
        }
    }

    @Test
    public void legacyNonDefaultManualProfileCanBeMigratedWithoutChangingItsId()
            throws Exception {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Patient original = new Patient();
        original.setName("Synthetic legacy nondefault profile");
        try {
            assertEquals(1, DB.patients().create(original));
            assertNull(original.getCode());
            Patient canonical = DB.patients()
                    .findByIdWithLocalIdentity(original.getId());
            assertNotNull(canonical);
            assertEquals(original.getId(), canonical.getId());
            assertNotNull(canonical.getCode());
            assertTrue(canonical.getCode().startsWith("local-"));
            assertEquals(canonical.getCode(),
                    DB.patients().findByIdWithLocalIdentity(original.getId()).getCode());
            canonical.setName("Migrated local profile edited");
            PatientProfilePersistence.save(canonical, context, false);
            assertEquals("Migrated local profile edited",
                    DB.patients().findById(original.getId()).getName());

            original.setName("Old editor must be rejected");
            boolean rejected = false;
            try {
                PatientProfilePersistence.save(original, context, false);
            } catch (RuntimeException expected) {
                rejected = true;
            }
            assertTrue("Stale anonymous editor must not overwrite migrated identity", rejected);
            assertEquals("Migrated local profile edited",
                    DB.patients().findById(original.getId()).getName());
        } finally {
            if (original.getId() != null && DB.patients().findById(original.getId()) != null) {
                DB.patients().remove(DB.patients().findById(original.getId()));
            }
        }
    }

    @Test
    public void failedOfflineProfileCreationRestoresTheDraftCodeForSafeRetry()
            throws Exception {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Patient draft = new Patient();
        draft.setName("Synthetic retryable offline profile");
        SQLiteDatabase sqlite = DB.helper().getWritableDatabase();
        String trigger = "ci_local_profile_identity_retry";
        sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
        try {
            sqlite.execSQL("CREATE TRIGGER " + trigger
                    + " BEFORE INSERT ON Patients"
                    + " BEGIN SELECT RAISE(ABORT, 'synthetic profile insert failure'); END;");
            boolean failed = false;
            try {
                PatientProfilePersistence.save(draft, context, false);
            } catch (RuntimeException expected) {
                failed = true;
            }
            assertTrue(failed);
            assertNull(draft.getId());
            assertNull("Failed insert must not lock a fresh identity into UI draft", draft.getCode());

            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            PatientProfilePersistence.save(draft, context, false);
            assertNotNull(draft.getId());
            assertNotNull(draft.getCode());
            assertTrue(draft.getCode().startsWith("local-"));
        } finally {
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            if (draft.getId() != null && DB.patients().findById(draft.getId()) != null) {
                DB.patients().remove(draft);
            }
        }
    }

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
