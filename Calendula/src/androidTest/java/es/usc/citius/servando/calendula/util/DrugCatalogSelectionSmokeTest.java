/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util;

import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Runs only against disposable CI data. Restores any pre-existing setting. */
@RunWith(AndroidJUnit4.class)
public class DrugCatalogSelectionSmokeTest {
    @Test
    public void actualAndroidWrongTypePreferencesNeverEnableCatalogOperations() {
        SharedPreferences prefs = PreferenceUtils.instance().preferences();
        String key = PreferenceKeys.DRUGDB_CURRENT_DB.key();
        Object saved = prefs.getAll().get(key);
        boolean present = prefs.contains(key);
        try {
            assertTrue(prefs.edit().putInt(key, 1234).commit());
            assertNull(DrugCatalogSelection.selectedOrNull());
            assertFalse(DrugCatalogSelection.isSelected("aemps"));
            assertFalse(DrugCatalogSelection.isReady("none", "setting_up"));

            assertTrue(prefs.edit().putBoolean(key, true).commit());
            assertNull(DrugCatalogSelection.selectedOrNull());
            assertFalse(DrugCatalogSelection.isReady("none", "setting_up"));

            assertTrue(prefs.edit().putString(key, "aemps").commit());
            assertTrue(DrugCatalogSelection.isSelected("aemps"));
            assertTrue(DrugCatalogSelection.isReady("none", "setting_up"));
        } finally {
            SharedPreferences.Editor editor = prefs.edit();
            if (!present || saved == null) {
                editor.remove(key);
            } else if (saved instanceof String) {
                editor.putString(key, (String) saved);
            } else if (saved instanceof Integer) {
                editor.putInt(key, (Integer) saved);
            } else if (saved instanceof Boolean) {
                editor.putBoolean(key, (Boolean) saved);
            } else if (saved instanceof Long) {
                editor.putLong(key, (Long) saved);
            } else if (saved instanceof Float) {
                editor.putFloat(key, (Float) saved);
            } else {
                throw new AssertionError("Unexpected original preference type");
            }
            assertTrue("Must restore the catalog selector after smoke test", editor.commit());
        }
    }
}
