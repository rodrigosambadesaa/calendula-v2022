/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class NotificationSoundPolicyTest {
    private static final Uri DEFAULT = Uri.parse("content://settings/system/notification_sound");

    @Test
    public void supportedAndroidRingtoneUrisArePreserved() {
        for (String tone : new String[]{
                "content://media/external/audio/media/123",
                "content://settings/system/notification_sound",
                "android.resource://es.usc.citius.servando.calendula/raw/chime",
                "file:///sdcard/Notifications/legacy.ogg"}) {
            assertEquals(Uri.parse(tone),
                    NotificationSoundPolicy.safeNotificationTone(tone, DEFAULT));
        }
    }

    @Test
    public void missingMalformedOrRemoteTonesFallBackToSystemSound() {
        for (String tone : new String[]{
                "", " ", "../tone.mp3", "https://remote.example/audio.ogg",
                "javascript:alert(1)", "data:audio/ogg;base64,AAAA",
                "content:relative", "android.resource:/without-authority",
                "content://", "file:", "content://media/audio\nmalformed",
                new String(new char[2100]).replace((char) 0, 'z')}) {
            assertEquals("Invalid tone: " + tone.substring(0, Math.min(40, tone.length())),
                    DEFAULT, NotificationSoundPolicy.safeNotificationTone(tone, DEFAULT));
        }
        assertEquals(DEFAULT, NotificationSoundPolicy.safeNotificationTone(null, DEFAULT));
    }
}
