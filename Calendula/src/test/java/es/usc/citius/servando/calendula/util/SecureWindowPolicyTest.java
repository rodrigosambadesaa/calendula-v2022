/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util;

import org.junit.Test;
import org.mockito.MockedStatic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mockStatic;

public class SecureWindowPolicyTest {
    @Test
    public void missingPreferenceKeepsClinicalScreensProtected() {
        try (MockedStatic<PreferenceUtils> preferences = mockStatic(PreferenceUtils.class)) {
            preferences.when(() -> PreferenceUtils.getBoolean(
                    PreferenceKeys.SECURE_WINDOW, true)).thenReturn(true);
            assertTrue(SecureWindowPolicy.isProtected());
        }
    }

    @Test
    public void wronglyTypedRestoredPreferenceFailsClosed() {
        try (MockedStatic<PreferenceUtils> preferences = mockStatic(PreferenceUtils.class)) {
            preferences.when(() -> PreferenceUtils.getBoolean(
                    PreferenceKeys.SECURE_WINDOW, true))
                    .thenThrow(new ClassCastException("Corrupt synthetic preference"));
            assertTrue(SecureWindowPolicy.isProtected());
        }
    }

    @Test
    public void explicitScreenshotOptOutIsRespected() {
        try (MockedStatic<PreferenceUtils> preferences = mockStatic(PreferenceUtils.class)) {
            preferences.when(() -> PreferenceUtils.getBoolean(
                    PreferenceKeys.SECURE_WINDOW, true)).thenReturn(false);
            assertFalse(SecureWindowPolicy.isProtected());
        }
    }
}
