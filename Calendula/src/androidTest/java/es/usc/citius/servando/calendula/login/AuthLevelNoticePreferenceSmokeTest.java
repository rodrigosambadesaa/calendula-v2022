/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.login;

import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Uses temporary settings only; never opens OAuth or mutates credentials. */
@RunWith(AndroidJUnit4.class)
public class AuthLevelNoticePreferenceSmokeTest {
    @Test public void malformedRestoredNoticeDoesNotCrashLoginPolicy() {
        SharedPreferences prefs = PreferenceUtils.instance().preferences();
        String key = PreferenceKeys.LAST_LOGIN_FAILED_AUTH_LEVEL.key();
        boolean hadOriginal = prefs.contains(key);
        Object previous = prefs.getAll().get(key);
        try {
            assertTrue(prefs.edit().putString(key, "legacy").commit());
            assertFalse(AuthLevelNoticePreferencePolicy.isPending(prefs));
            assertTrue(prefs.edit().putInt(key, 1).commit());
            assertFalse(AuthLevelNoticePreferencePolicy.isPending(prefs));
            assertTrue(prefs.edit().putBoolean(key, true).commit());
            assertTrue(AuthLevelNoticePreferencePolicy.isPending(prefs));
            assertTrue(prefs.edit().putBoolean(key, false).commit());
            assertFalse(AuthLevelNoticePreferencePolicy.isPending(prefs));
        } finally {
            SharedPreferences.Editor restore = prefs.edit();
            if (!hadOriginal || previous == null) {
                restore.remove(key);
            } else if (previous instanceof Boolean) {
                restore.putBoolean(key, (Boolean) previous);
            } else if (previous instanceof String) {
                restore.putString(key, (String) previous);
            } else if (previous instanceof Integer) {
                restore.putInt(key, (Integer) previous);
            } else if (previous instanceof Long) {
                restore.putLong(key, (Long) previous);
            } else if (previous instanceof Float) {
                restore.putFloat(key, (Float) previous);
            } else {
                throw new AssertionError("Unknown original login notice type");
            }
            assertTrue(restore.commit());
        }
    }
}
