/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util;

import android.content.SharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Does not initialize or reset the application's global preferences. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PreferenceUtilsDefaultsStartupTest {
    private static final String KEY = "pref_defaults_loaded";

    @Test
    public void priorSuccessfulInitializationMustNotRerunDefaultInstallation() {
        SharedPreferences prefs = mock(SharedPreferences.class);
        when(prefs.getBoolean(KEY, false)).thenReturn(true);
        assertFalse(PreferenceUtils.needsDefaultInitialization(prefs, KEY));
    }

    @Test
    public void missingInitializationMarkerStillInstallsDefaults() {
        SharedPreferences prefs = mock(SharedPreferences.class);
        when(prefs.getBoolean(KEY, false)).thenReturn(false);
        assertTrue(PreferenceUtils.needsDefaultInitialization(prefs, KEY));
    }

    @Test
    public void incompatibleLegacyMarkerDoesNotAbortStartup() {
        SharedPreferences prefs = mock(SharedPreferences.class);
        when(prefs.getBoolean(KEY, false))
                .thenThrow(new ClassCastException("synthetic legacy String"));
        assertTrue(PreferenceUtils.needsDefaultInitialization(prefs, KEY));
    }
}
