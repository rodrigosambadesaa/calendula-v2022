/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.pinlock.fingerprint;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** No fingerprint enrollment, PIN material or keystore is read in these tests. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class FingerprintAvailabilitySafetyTest {
    @Test
    public void deviceWithoutFingerprintServicesStillOffersPinFallback() {
        Context context = mock(Context.class);
        when(context.getApplicationContext()).thenReturn(context);
        // Both getSystemService() calls intentionally return null.
        FingerprintHelper helper = new FingerprintHelper(context);
        assertFalse(helper.canUseFingerPrint());
    }

    @Test
    public void wrongTypeRestoredFingerprintToggleCannotAuthorizeBiometrics() {
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getBoolean(
                    PreferenceKeys.FINGERPRINT_ENABLED, false))
                    .thenThrow(new ClassCastException("Synthetic restored String"));
            assertFalse(FingerprintHelper.safelyConfiguredFingerprintEnabled());
        }
    }

    @Test
    public void explicitOptInAndOptOutRemainRespected() {
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getBoolean(
                    PreferenceKeys.FINGERPRINT_ENABLED, false)).thenReturn(true);
            assertTrue(FingerprintHelper.safelyConfiguredFingerprintEnabled());
        }
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getBoolean(
                    PreferenceKeys.FINGERPRINT_ENABLED, false)).thenReturn(false);
            assertFalse(FingerprintHelper.safelyConfiguredFingerprintEnabled());
        }
    }
}
