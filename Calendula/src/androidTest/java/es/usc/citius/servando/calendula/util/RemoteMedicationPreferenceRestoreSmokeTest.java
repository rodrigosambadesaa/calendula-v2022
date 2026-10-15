/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util;

import android.content.SharedPreferences;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Disposable app/emulator only. Never accesses remote account or clinical rows. */
@RunWith(AndroidJUnit4.class)
public class RemoteMedicationPreferenceRestoreSmokeTest {
    @Test
    public void wrongTypeMetadataCannotCrashProviderStatusOrClaimOldRevision() {
        SharedPreferences prefs = PreferenceUtils.instance().preferences();
        PreferenceKeys[] keys = {
                PreferenceKeys.REMOTE_LAST_HASH,
                PreferenceKeys.REMOTE_UPDATE_STATUS,
                PreferenceKeys.REMOTE_LAST_GOOD_UPDATE_SUMMARY};
        Object[] original = new Object[keys.length];
        boolean[] had = new boolean[keys.length];
        for (int i = 0; i < keys.length; i++) {
            String key = keys[i].key();
            had[i] = prefs.contains(key);
            original[i] = prefs.getAll().get(key);
        }
        try {
            for (PreferenceKeys key : keys) {
                assertTrue(prefs.edit().putInt(key.key(), 42).commit());
            }
            assertNull(RemoteMedicationPreferenceReader.lastRevisionOrNull());
            assertNull(RemoteMedicationPreferenceReader.readOrDefault(
                    PreferenceKeys.REMOTE_UPDATE_STATUS, null));
            assertEquals("", RemoteMedicationPreferenceReader.readOrDefault(
                    PreferenceKeys.REMOTE_LAST_GOOD_UPDATE_SUMMARY, ""));
        } finally {
            SharedPreferences.Editor edit = prefs.edit();
            for (int i = 0; i < keys.length; i++) {
                String key = keys[i].key();
                Object previous = original[i];
                if (!had[i] || previous == null) {
                    edit.remove(key);
                } else if (previous instanceof String) {
                    edit.putString(key, (String) previous);
                } else if (previous instanceof Integer) {
                    edit.putInt(key, (Integer) previous);
                } else if (previous instanceof Boolean) {
                    edit.putBoolean(key, (Boolean) previous);
                } else if (previous instanceof Long) {
                    edit.putLong(key, (Long) previous);
                } else if (previous instanceof Float) {
                    edit.putFloat(key, (Float) previous);
                } else {
                    throw new AssertionError("Unexpected original provider metadata type");
                }
            }
            assertTrue("Synthetic test must restore provider preferences", edit.commit());
        }
    }
}
