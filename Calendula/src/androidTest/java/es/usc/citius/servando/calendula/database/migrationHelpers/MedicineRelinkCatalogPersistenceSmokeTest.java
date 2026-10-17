/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.database.migrationHelpers;

import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Real SQLite and preferences with synthetic fixtures only.
 * No remote catalog is installed or queried when selection is malformed.
 */
@RunWith(AndroidJUnit4.class)
public class MedicineRelinkCatalogPersistenceSmokeTest {
    @Test
    public void damagedOrRetiredSelectionNeverRewritesExistingPatientMedicine() {
        assertTrue(DB.initialized);
        SharedPreferences prefs = PreferenceUtils.instance().preferences();
        String key = PreferenceKeys.DRUGDB_CURRENT_DB.key();
        boolean previousPresent = prefs.contains(key);
        Object previous = prefs.getAll().get(key);

        Patient patient = new Patient();
        patient.setName("Synthetic relink policy patient");
        patient.setCode("ci-relink-policy-" + System.nanoTime());
        Medicine medicine = new Medicine("Synthetic unchanged medication", Presentation.PILLS);
        medicine.setCn("ci-no-real-cn-" + System.nanoTime());
        medicine.setDatabase(InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getString(R.string.database_aemps_id));
        try {
            assertEquals(1, DB.patients().create(patient));
            medicine.setPatient(patient);
            assertEquals(1, DB.medicines().create(medicine));
            assertNotNull(medicine.getId());

            assertTrue(prefs.edit().putInt(key, 12345).commit());
            DrugModelMigrationHelper.linkMedsAfterUpdate();
            assertUnchanged(medicine);

            assertTrue(prefs.edit().putString(key, "removed_provider_" + System.nanoTime()).commit());
            DrugModelMigrationHelper.linkMedsAfterUpdate();
            assertUnchanged(medicine);

            assertTrue(prefs.edit().putString(key, " ").commit());
            DrugModelMigrationHelper.linkMedsAfterUpdate();
            assertUnchanged(medicine);
        } finally {
            SharedPreferences.Editor restore = prefs.edit();
            if (!previousPresent || previous == null) {
                restore.remove(key);
            } else if (previous instanceof String) {
                restore.putString(key, (String) previous);
            } else if (previous instanceof Boolean) {
                restore.putBoolean(key, (Boolean) previous);
            } else if (previous instanceof Integer) {
                restore.putInt(key, (Integer) previous);
            } else if (previous instanceof Long) {
                restore.putLong(key, (Long) previous);
            } else if (previous instanceof Float) {
                restore.putFloat(key, (Float) previous);
            } else if (previous instanceof Set) {
                @SuppressWarnings("unchecked")
                Set<String> set = (Set<String>) previous;
                restore.putStringSet(key, set);
            } else {
                throw new AssertionError("Unsupported original preference type");
            }
            assertTrue("Restore saved catalog selection after synthetic test", restore.commit());
            if (medicine.getId() != null && DB.medicines().findById(medicine.getId()) != null) {
                DB.medicines().remove(medicine);
            }
            if (patient.getId() != null && DB.patients().findById(patient.getId()) != null) {
                DB.patients().remove(patient);
            }
        }
    }

    private static void assertUnchanged(Medicine fixture) {
        Medicine persisted = DB.medicines().findById(fixture.getId());
        assertNotNull(persisted);
        assertEquals("Synthetic unchanged medication", persisted.getName());
        assertEquals(fixture.getDatabase(), persisted.getDatabase());
        assertEquals(fixture.getPatient().getId(), persisted.getPatient().getId());
    }
}
