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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Synthetic startup warning; never changes the actual device security state. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class StartupSecurityWarningPolicyTest {
    private static final String CONSENT = "vulnerability_warning_accepted_by_user";

    @Test
    public void missingOrExplicitDenialRequiresWarningAgain() {
        SharedPreferences settings = mock(SharedPreferences.class);
        when(settings.getBoolean(CONSENT, false)).thenReturn(false);
        assertFalse(StartupSecurityWarningPolicy.wasAccepted(settings, CONSENT));
    }

    @Test
    public void priorExplicitBooleanAcceptanceIsPreserved() {
        SharedPreferences settings = mock(SharedPreferences.class);
        when(settings.getBoolean(CONSENT, false)).thenReturn(true);
        assertTrue(StartupSecurityWarningPolicy.wasAccepted(settings, CONSENT));
    }

    @Test
    public void wrongTypeRestoredConsentNeverBypassesStartupWarning() {
        SharedPreferences settings = mock(SharedPreferences.class);
        when(settings.getBoolean(CONSENT, false))
                .thenThrow(new ClassCastException("Synthetic incompatible String"));
        assertFalse(StartupSecurityWarningPolicy.wasAccepted(settings, CONSENT));
    }
}
