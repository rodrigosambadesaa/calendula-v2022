/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import org.junit.Test;
import org.mockito.MockedStatic;

import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mockStatic;

public class NotificationFlagPolicyTest {
    @Test
    public void incompatibleNotificationEnabledSwitchCannotDropMedicationNotices() {
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getBoolean(
                    PreferenceKeys.SETTINGS_ALARM_NOTIFICATIONS, true))
                    .thenThrow(new ClassCastException("restored string"));
            assertTrue(NotificationFlagPolicy.configuredOrDefault(
                    PreferenceKeys.SETTINGS_ALARM_NOTIFICATIONS, true));
        }
    }

    @Test
    public void incompatibleInsistentSwitchCannotLaunchUnexpectedFullscreenAlert() {
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getBoolean(
                    PreferenceKeys.SETTINGS_ALARM_INSISTENT, false))
                    .thenThrow(new ClassCastException("restored integer"));
            assertFalse(NotificationFlagPolicy.configuredOrDefault(
                    PreferenceKeys.SETTINGS_ALARM_INSISTENT, false));
        }
    }

    @Test
    public void configuredTrueAndFalseSwitchesRemainAuthoritative() {
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getBoolean(
                    PreferenceKeys.SETTINGS_ALARM_NOTIFICATIONS, true)).thenReturn(false);
            prefs.when(() -> PreferenceUtils.getBoolean(
                    PreferenceKeys.SETTINGS_ALARM_INSISTENT, false)).thenReturn(true);
            assertFalse(NotificationFlagPolicy.configuredOrDefault(
                    PreferenceKeys.SETTINGS_ALARM_NOTIFICATIONS, true));
            assertTrue(NotificationFlagPolicy.configuredOrDefault(
                    PreferenceKeys.SETTINGS_ALARM_INSISTENT, false));
        }
    }
}
