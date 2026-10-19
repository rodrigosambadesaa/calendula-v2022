package es.usc.citius.servando.calendula.util;

import android.content.SharedPreferences;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Patient;
import static org.junit.Assert.*;

/**
 * Synthetic SQLite patients and app-local preferences. No remote server calls
 * and no medication, dose, alarm or real patient records are used.
 */
@RunWith(AndroidJUnit4.class)
public class ProviderRevisionOwnershipSmokeTest {
    @Test
    public void twoPatientsCannotReuseEachOthersStoredProviderRevision() throws Exception {
        assertTrue(DB.initialized);
        String unique = Long.toString(System.nanoTime());
        Patient a = patient("ci-revision-a-" + unique);
        Patient b = patient("ci-revision-b-" + unique);
        SharedPreferences prefs = PreferenceUtils.instance().preferences();
        String hashKey = PreferenceKeys.REMOTE_LAST_HASH.key();
        String ownerKey = PreferenceKeys.REMOTE_LAST_HASH_OWNER.key();
        Object previousHash = prefs.getAll().get(hashKey);
        Object previousOwner = prefs.getAll().get(ownerKey);
        try {
            assertEquals(1, DB.patients().create(a));
            assertEquals(1, DB.patients().create(b));
            String aOwner = ProviderRevisionGuard.ownerKey(a.getId(), a.getCode());
            assertNotNull(aOwner);
            assertTrue(prefs.edit().putString(hashKey, "synthetic-a-revision")
                    .putString(ownerKey, aOwner).commit());

            assertEquals("synthetic-a-revision",
                    ProviderRevisionGuard.lastRevisionForPatient(a, a.getCode()));
            assertNull("Patient B cannot send A's conditional revision",
                    ProviderRevisionGuard.lastRevisionForPatient(b, b.getCode()));
            assertNull("A stale authenticated subject cannot use A's revision",
                    ProviderRevisionGuard.lastRevisionForPatient(a, b.getCode()));

            assertTrue(prefs.edit().remove(ownerKey).commit());
            assertNull("Pre-migration unowned revision requires fresh verification",
                    ProviderRevisionGuard.lastRevisionForPatient(a, a.getCode()));
            assertTrue(prefs.edit().putInt(ownerKey, 123).commit());
            assertNull("Corrupt restored owner metadata must fail closed",
                    ProviderRevisionGuard.lastRevisionForPatient(a, a.getCode()));
        } finally {
            SharedPreferences.Editor restore = prefs.edit();
            restore(restore, hashKey, previousHash);
            restore(restore, ownerKey, previousOwner);
            assertTrue(restore.commit());
            for (Patient patient : new Patient[]{a, b}) {
                if (patient.getId() != null && DB.patients().findById(patient.getId()) != null) {
                    DB.patients().remove(patient);
                }
            }
        }
    }

    private static void restore(SharedPreferences.Editor e, String key, Object original) {
        if (original == null) e.remove(key);
        else if (original instanceof String) e.putString(key, (String) original);
        else if (original instanceof Boolean) e.putBoolean(key, (Boolean) original);
        else if (original instanceof Integer) e.putInt(key, (Integer) original);
        else if (original instanceof Long) e.putLong(key, (Long) original);
        else if (original instanceof Float) e.putFloat(key, (Float) original);
        else throw new IllegalStateException("Unexpected restored preference type");
    }

    private static Patient patient(String subject) {
        Patient p = new Patient();
        p.setCode(subject);
        p.setName("Synthetic owner for revision isolation");
        return p;
    }
}
