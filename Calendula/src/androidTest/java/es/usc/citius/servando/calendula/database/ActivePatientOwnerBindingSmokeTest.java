package es.usc.citius.servando.calendula.database;

import android.content.Context;
import android.content.SharedPreferences;
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
}
