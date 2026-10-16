/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Real Android preference test. Does not open clinical data or alter consent permanently. */
@RunWith(AndroidJUnit4.class)
public class AllergiesWarningPreferenceSmokeTest {
    @Test
    public void corruptedAcknowledgementAlwaysRequiresWarning() {
        SharedPreferences prefs = PreferenceUtils.instance().preferences();
        String key = PreferenceKeys.ALLERGIES_WARNING_SHOWN.key();
        boolean present = prefs.contains(key);
        Object old = prefs.getAll().get(key);
        try {
            assertTrue(prefs.edit().putString(key, "yes").commit());
            assertTrue(AllergiesWarningPreferencePolicy.shouldShowWarning(prefs));
            assertTrue(prefs.edit().putInt(key, 1).commit());
            assertTrue(AllergiesWarningPreferencePolicy.shouldShowWarning(prefs));
            assertTrue(prefs.edit().putBoolean(key, true).commit());
            assertFalse(AllergiesWarningPreferencePolicy.shouldShowWarning(prefs));
            assertTrue(prefs.edit().putBoolean(key, false).commit());
            assertTrue(AllergiesWarningPreferencePolicy.shouldShowWarning(prefs));
        } finally {
            SharedPreferences.Editor restore = prefs.edit();
            if (!present || old == null) {
                restore.remove(key);
            } else if (old instanceof Boolean) {
                restore.putBoolean(key, (Boolean) old);
            } else if (old instanceof String) {
                restore.putString(key, (String) old);
            } else if (old instanceof Integer) {
                restore.putInt(key, (Integer) old);
            } else if (old instanceof Long) {
                restore.putLong(key, (Long) old);
            } else if (old instanceof Float) {
                restore.putFloat(key, (Float) old);
            } else {
                throw new AssertionError("Unsupported preference type during test cleanup");
            }
            assertTrue("Restore original preference even on failure", restore.commit());
        }
    }
}
