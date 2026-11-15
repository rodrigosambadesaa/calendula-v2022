package es.usc.citius.servando.calendula.database;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;
import static org.junit.Assert.*;

/**
 * Real SQLite and SharedPreferences, disposable emulator data only.
 * Never run this instrumentation on an actual user's medication database.
 */
@RunWith(AndroidJUnit4.class)
public class ActivePatientOwnerBindingSmokeTest {
    private static void restore(SharedPreferences.Editor edit, String key, Object original) {
        if (original == null) edit.remove(key);
        else if (original instanceof String) edit.putString(key, (String) original);
        else if (original instanceof Long) edit.putLong(key, (Long) original);
        else if (original instanceof Integer) edit.putInt(key, (Integer) original);
        else if (original instanceof Boolean) edit.putBoolean(key, (Boolean) original);
        else if (original instanceof Float) edit.putFloat(key, (Float) original);
        else throw new AssertionError("Unexpected preference type in disposable test");
    }

    /**
     * Regression for the post-COMMIT erasure preference cleanup: an erased
     * owner must not clear a newly selected patient if SQLite reuses the
     * same row ID. Numeric equality alone is deliberately insufficient.
     */
    @Test
    public void reusedNumericIdCannotInvalidateNewOwnerSelection() {
        Patient erased = new Patient();
        erased.setId(67890L);
        erased.setCode("ci-erased-owner-regression");
        Patient replacement = new Patient();
        replacement.setId(erased.getId());
        replacement.setCode("ci-replacement-owner-regression");

        String erasedDigest = PatientDao.stablePatientDigest(erased.getCode());
        String newDigest = PatientDao.stablePatientDigest(replacement.getCode());
        assertNotNull(erasedDigest);
        assertNotNull(newDigest);
        assertNotEquals(erasedDigest, newDigest);

        assertTrue("The erased owner's exact identity may be invalidated",
                PatientDao.matchesDeletedActiveBinding(
                        erased, erased.getId(), erasedDigest));
        assertFalse("A replacement patient reusing the row ID must survive",
                PatientDao.matchesDeletedActiveBinding(
                        erased, replacement.getId(), newDigest));
        assertFalse("A different row ID cannot authorize deletion",
                PatientDao.matchesDeletedActiveBinding(
                        erased, erased.getId() + 1L, erasedDigest));
        assertFalse("A missing binding must fail closed",
                PatientDao.matchesDeletedActiveBinding(erased, erased.getId(), null));
        assertFalse("Malformed binding must fail closed",
                PatientDao.matchesDeletedActiveBinding(erased, erased.getId(), "invalid"));
    }

    @Test
    public void retainedDigestRejectsRecycledNumericIdAndAllowsExplicitReselection()
            throws Exception {
        assertTrue(DB.initialized);
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs = PreferenceUtils.instance().preferences();
        String idKey = PreferenceKeys.PATIENTS_ACTIVE.key();
        String bindingKey = PreferenceKeys.PATIENTS_ACTIVE_OWNER.key();
        Object priorId = prefs.getAll().get(idKey);
        Object priorBinding = prefs.getAll().get(bindingKey);
        String markerKey = PreferenceKeys.PATIENTS_RESELECTION_REQUIRED.key();
        Object priorMarker = prefs.getAll().get(markerKey);

        Patient first = new Patient();
        first.setCode("local-ci-owner-A-" + System.nanoTime());
        first.setName("Synthetic A");
        Patient replacement = new Patient();
        replacement.setCode("local-ci-owner-B-" + System.nanoTime());
        replacement.setName("Synthetic B");
        try {
            assertEquals(1, DB.patients().create(first));
            assertEquals(1, DB.patients().create(replacement));
            DB.patients().setActive(first);
            assertEquals(first.getId(), DB.patients().getActive(ctx).getId());
            String digestA = prefs.getString(bindingKey, null);
            assertNotNull(digestA);
            assertTrue(digestA.matches("[0-9a-f]{64}"));
            assertFalse(digestA.contains(first.getCode()));

            // This represents an imported/restored SQLite row receiving the
            // old selected numeric ID but a different stable patient code.
            assertTrue(prefs.edit().putLong(idKey, replacement.getId()).commit());
            assertNull(DB.patients().getActive(ctx));
            assertFalse(DB.patients().isActive(replacement, ctx));

            // An ID-only convenience call must not silently turn a recycled
            // SQLite ID into authorization for the replacement profile.
            boolean rejectedIdOnly = false;
            try {
                DB.patients().setActiveById(replacement.getId());
            } catch (IllegalArgumentException expected) {
                rejectedIdOnly = true;
            }
            assertTrue("Recycled numeric ID cannot be used to switch owners", rejectedIdOnly);
            assertEquals("Original binding remains intact", digestA,
                    prefs.getString(bindingKey, null));

            // A legacy numeric ID with no binding cannot identify its owner.
            assertTrue(prefs.edit().remove(bindingKey).commit());
            assertNull(DB.patients().getActive(ctx));

            // Only an explicit selection can renew both saved coordinates.
            DB.patients().setActive(replacement);
            assertEquals(replacement.getId(), DB.patients().getActive(ctx).getId());
            assertNotEquals(digestA, prefs.getString(bindingKey, null));

            // A subsequently deleted selected row must not silently fall
            // back to the other local patient on the very next read.
            DB.patients().remove(replacement);
            assertNull(DB.patients().getActive(ctx));
            assertFalse(prefs.contains(idKey));
            assertFalse(prefs.contains(bindingKey));
            assertTrue(prefs.getBoolean(markerKey, false));
            assertNull("An invalidated selection remains invalid after recreation",
                    DB.patients().getActive(ctx));
        } finally {
            SharedPreferences.Editor edit = prefs.edit();
            restore(edit, idKey, priorId);
            restore(edit, bindingKey, priorBinding);
            restore(edit, markerKey, priorMarker);
            assertTrue(edit.commit());
            if (first.getId() != null && DB.patients().findById(first.getId()) != null)
                DB.patients().remove(first);
            if (replacement.getId() != null
                    && DB.patients().findById(replacement.getId()) != null)
                DB.patients().remove(replacement);
        }
    }

    /**
     * Integration-only regression for the overlapping #950 / #951 change:
     * a failed SQLite erasure must preserve the active owner binding, whereas
     * a committed erasure must invalidate both coordinates and require explicit
     * reselection. The fixture uses only synthetic local patient records.
     */
    @Test
    public void failedErasureKeepsActiveBindingAndCommittedErasureRequiresReselection()
            throws Exception {
        assertTrue(DB.initialized);
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs = PreferenceUtils.instance().preferences();
        final String idKey = PreferenceKeys.PATIENTS_ACTIVE.key();
        final String bindingKey = PreferenceKeys.PATIENTS_ACTIVE_OWNER.key();
        final String markerKey = PreferenceKeys.PATIENTS_RESELECTION_REQUIRED.key();
        Object priorId = prefs.getAll().get(idKey);
        Object priorBinding = prefs.getAll().get(bindingKey);
        Object priorMarker = prefs.getAll().get(markerKey);
        SQLiteDatabase sqlite = DB.helper().getWritableDatabase();
        final String trigger = "ci_owner_binding_erase_abort";
        Patient owner = new Patient();
        owner.setCode("ci-bound-erase-" + System.nanoTime());
        owner.setName("Synthetic selected owner");
        Patient survivor = new Patient();
        survivor.setCode("ci-bound-survivor-" + System.nanoTime());
        survivor.setName("Synthetic surviving owner");

        sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
        try {
            assertEquals(1, DB.patients().create(owner));
            assertEquals(1, DB.patients().create(survivor));
            DB.patients().setActive(owner);
            String binding = prefs.getString(bindingKey, null);
            assertNotNull(binding);
            assertEquals(owner.getId(), DB.patients().getActive(ctx).getId());
            assertFalse(prefs.getBoolean(markerKey, false));

            // Inject a late failure after SQL-only cascade cleanup started.
            sqlite.execSQL("CREATE TRIGGER " + trigger
                    + " BEFORE DELETE ON Patients WHEN OLD._id = " + owner.getId()
                    + " BEGIN SELECT RAISE(ABORT, 'synthetic owner binding rollback'); END;");
            boolean rejected = false;
            try {
                DB.patients().removeCascade(owner);
            } catch (RuntimeException expected) {
                rejected = true;
            }
            assertTrue("Late failed delete must roll back", rejected);
            assertEquals(owner.getId(), DB.patients().getActive(ctx).getId());
            assertEquals("Failed deletion retains the saved stable binding",
                    binding, prefs.getString(bindingKey, null));
            assertEquals(owner.getId().longValue(), prefs.getLong(idKey, -1L));
            assertFalse("Failed deletion must not require reselection",
                    prefs.getBoolean(markerKey, false));
            assertNotNull(DB.patients().findById(survivor.getId()));

            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            DB.patients().removeCascade(owner);
            assertNull(DB.patients().findById(owner.getId()));
            assertFalse("Committed erasure clears saved numeric identity",
                    prefs.contains(idKey));
            assertFalse("Committed erasure clears saved stable identity",
                    prefs.contains(bindingKey));
            assertTrue("Committed erasure requires an explicit owner choice",
                    prefs.getBoolean(markerKey, false));
            assertNull("No unrelated default owner may become active",
                    DB.patients().getActive(ctx));
            assertNotNull("Unrelated profile must be preserved",
                    DB.patients().findById(survivor.getId()));
        } finally {
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            SharedPreferences.Editor editor = prefs.edit();
            restore(editor, idKey, priorId);
            restore(editor, bindingKey, priorBinding);
            restore(editor, markerKey, priorMarker);
            assertTrue(editor.commit());
            for (Patient patient : new Patient[]{owner, survivor}) {
                if (patient.getId() != null && DB.patients().findById(patient.getId()) != null) {
                    DB.patients().remove(patient);
                }
            }
        }
    }
}
