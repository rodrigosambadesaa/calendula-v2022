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

/** Synthetic preference tests; no credentials or patient data are touched. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class LoginSkipPreferenceSafetyTest {
    private static final String KEY = PreferenceKeys.LOGIN_SKIP_LOGIN.key();

    @Test
    public void explicitSkipRemainsAllowed() {
        SharedPreferences settings = mock(SharedPreferences.class);
        when(settings.getBoolean(KEY, false)).thenReturn(true);
        assertTrue(LoginStateManager.explicitLoginSkipAccepted(settings));
    }

    @Test
    public void absentOrExplicitlyDisabledSkipRequiresLogin() {
        SharedPreferences settings = mock(SharedPreferences.class);
        when(settings.getBoolean(KEY, false)).thenReturn(false);
        assertFalse(LoginStateManager.explicitLoginSkipAccepted(settings));
    }

    @Test
    public void restoredStringOrIntegerCannotSkipAuthentication() {
        SharedPreferences settings = mock(SharedPreferences.class);
        when(settings.getBoolean(KEY, false))
                .thenThrow(new ClassCastException("Synthetic legacy String"));
        assertFalse(LoginStateManager.explicitLoginSkipAccepted(settings));
    }
}
