/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

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
public class AllergiesWarningPreferencePolicyTest {
    private static final String KEY = PreferenceKeys.ALLERGIES_WARNING_SHOWN.key();

    @Test
    public void missingOrUnacknowledgedNoticeMustBeShown() {
        SharedPreferences prefs = mock(SharedPreferences.class);
        when(prefs.getBoolean(KEY, false)).thenReturn(false);
        assertTrue(AllergiesWarningPreferencePolicy.shouldShowWarning(prefs));
        assertTrue(AllergiesWarningPreferencePolicy.shouldShowWarning(null));
    }

    @Test
    public void onlyExplicitBooleanTrueSkipsNotice() {
        SharedPreferences prefs = mock(SharedPreferences.class);
        when(prefs.getBoolean(KEY, false)).thenReturn(true);
        assertFalse(AllergiesWarningPreferencePolicy.shouldShowWarning(prefs));
    }

    @Test
    public void wrongTypeRestoredNoticeNeverSilentlyCountsAsAcknowledged() {
        SharedPreferences prefs = mock(SharedPreferences.class);
        when(prefs.getBoolean(KEY, false)).thenThrow(new ClassCastException("Synthetic restored String"));
        assertTrue(AllergiesWarningPreferencePolicy.shouldShowWarning(prefs));
    }
}
