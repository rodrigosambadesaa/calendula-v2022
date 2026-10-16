/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.login;

import android.content.SharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.util.PreferenceKeys;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AuthLevelNoticePreferencePolicyTest {
    private static final String KEY =
            PreferenceKeys.LAST_LOGIN_FAILED_AUTH_LEVEL.key();

    @Test public void onlyExplicitBooleanTrueDisplaysPreviousAuthFailureNotice() {
        SharedPreferences prefs = mock(SharedPreferences.class);
        when(prefs.getBoolean(KEY, false)).thenReturn(true);
        assertTrue(AuthLevelNoticePreferencePolicy.isPending(prefs));
        when(prefs.getBoolean(KEY, false)).thenReturn(false);
        assertFalse(AuthLevelNoticePreferencePolicy.isPending(prefs));
        assertFalse(AuthLevelNoticePreferencePolicy.isPending(null));
    }

    @Test public void invalidLegacyValueCannotCrashAuthenticationUi() {
        SharedPreferences prefs = mock(SharedPreferences.class);
        when(prefs.getBoolean(KEY, false))
                .thenThrow(new ClassCastException("Synthetic saved String"));
        assertFalse(AuthLevelNoticePreferencePolicy.isPending(prefs));
    }
}
