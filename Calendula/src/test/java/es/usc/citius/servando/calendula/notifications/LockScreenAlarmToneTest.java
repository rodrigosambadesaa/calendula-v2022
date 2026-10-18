/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.notifications;

import android.media.RingtoneManager;
import android.net.Uri;
import android.provider.Settings;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mockStatic;

/** Only selects a tone; does not play media, vibrate or send notifications. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class LockScreenAlarmToneTest {
    private static Uri systemTone() {
        Uri system = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        return system != null ? system : Settings.System.DEFAULT_ALARM_ALERT_URI;
    }

    @Test
    public void restoredWrongTypeUsesLocalSystemAlarmInsteadOfCrashing() {
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getString(
                    PreferenceKeys.SETTINGS_INSISTENT_NOTIFICATION_TONE, null))
                    .thenThrow(new ClassCastException("Synthetic integer"));
            assertEquals(systemTone(), LockScreenAlarmActivity.configuredAlarmTone());
        }
    }

    @Test
    public void oldRemoteScriptOrMalformedUriCannotBeLoadedAsAlarm() {
        for (String invalid : new String[]{
                "", "https://example.test/alarm.mp3", "javascript:alert(1)",
                "content:relative", "file:", "data:audio/mp3;base64,AAA"}) {
            try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
                prefs.when(() -> PreferenceUtils.getString(
                        PreferenceKeys.SETTINGS_INSISTENT_NOTIFICATION_TONE, null))
                        .thenReturn(invalid);
                assertEquals(systemTone(), LockScreenAlarmActivity.configuredAlarmTone());
            }
        }
    }

    @Test
    public void validLocalToneRemainsSelected() {
        String local = "content://media/external/audio/media/123";
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getString(
                    PreferenceKeys.SETTINGS_INSISTENT_NOTIFICATION_TONE, null))
                    .thenReturn(local);
            assertEquals(Uri.parse(local), LockScreenAlarmActivity.configuredAlarmTone());
        }
    }
}
