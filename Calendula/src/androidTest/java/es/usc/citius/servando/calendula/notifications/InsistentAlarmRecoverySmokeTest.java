/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.provider.Settings;

import androidx.core.content.ContextCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Only checks framework settings and private receiver registration; no alarm is started. */
@RunWith(AndroidJUnit4.class)
public class InsistentAlarmRecoverySmokeTest {
    @Test
    public void corruptSavedToneNeverGeneratesARemoteOrBrokenAlarmUri() {
        final SharedPreferences prefs = PreferenceUtils.instance().preferences();
        final String key = PreferenceKeys.SETTINGS_INSISTENT_NOTIFICATION_TONE.key();
        final boolean hadValue = prefs.contains(key);
        final Object oldValue = prefs.getAll().get(key);
        Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        final Uri fallback = alarmUri != null
                ? alarmUri : Settings.System.DEFAULT_ALARM_ALERT_URI;
        try {
            assertTrue(prefs.edit().putInt(key, 42).commit());
            assertEquals(fallback, LockScreenAlarmActivity.configuredAlarmTone());
            assertTrue(prefs.edit().putString(key, "https://invalid.example/tone.mp3").commit());
            assertEquals(fallback, LockScreenAlarmActivity.configuredAlarmTone());
            Uri selected = Uri.parse("content://media/external/audio/media/123");
            assertTrue(prefs.edit().putString(key, selected.toString()).commit());
            assertEquals(selected, LockScreenAlarmActivity.configuredAlarmTone());
        } finally {
            SharedPreferences.Editor restore = prefs.edit();
            if (!hadValue || oldValue == null) {
                restore.remove(key);
            } else if (oldValue instanceof String) {
                restore.putString(key, (String) oldValue);
            } else if (oldValue instanceof Integer) {
                restore.putInt(key, (Integer) oldValue);
            } else if (oldValue instanceof Boolean) {
                restore.putBoolean(key, (Boolean) oldValue);
            } else if (oldValue instanceof Long) {
                restore.putLong(key, (Long) oldValue);
            } else if (oldValue instanceof Float) {
                restore.putFloat(key, (Float) oldValue);
            } else {
                throw new AssertionError("Unexpected original alarm preference type");
            }
            assertTrue("Must restore CI app preferences", restore.commit());
        }
    }

    @Test
    public void notExportedAlarmStopReceiverCanRegisterOnLegacyAndModernAndroid() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context receivingContext, Intent intent) {
                // No clinical work. Merely verify registration on the platform.
            }
        };
        ContextCompat.registerReceiver(context, receiver,
                new IntentFilter("es.usc.citius.calendula.TEST_LOCAL_ALARM_STOP"),
                ContextCompat.RECEIVER_NOT_EXPORTED);
        context.unregisterReceiver(receiver);
    }
}
