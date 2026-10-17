/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.scheduling.AlarmReceiver;
import es.usc.citius.servando.calendula.util.IntentParams;
import es.usc.citius.servando.calendula.util.PendingIntentFlags;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

/** Only creates and cancels inert PendingIntents: no broadcast is sent. */
@RunWith(AndroidJUnit4.class)
public class ReminderNotification64BitIdentitySmokeTest {
    private static PendingIntent token(Context context, long reminderId, String consent) {
        Intent action = new Intent(context, AlarmReceiver.class)
                .putExtra(IntentParams.EXTRA_ACTION, IntentParams.ACTION_ALARM_CONFIRM)
                .putExtra(IntentParams.EXTRA_REMINDER_ID, reminderId)
                .putExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT, consent);
        return PendingIntent.getBroadcast(context, (int) reminderId,
                IntakeNotificationMgr.identifyNotificationAction(
                        action, "CONFIRM", reminderId),
                PendingIntentFlags.immutable(PendingIntent.FLAG_UPDATE_CURRENT));
    }

    @Test
    public void sameLow32BitsCannotCrossWirePatientsMedicationButtons() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final long firstId = 1L;
        final long secondId = (1L << 32) + firstId;
        final String firstConsent = "a" + new String(new char[63]).replace((char) 0, '0');
        final String secondConsent = "b" + new String(new char[63]).replace((char) 0, '0');
        assertEquals((int) firstId, (int) secondId);

        PendingIntent first = token(context, firstId, firstConsent);
        PendingIntent second = token(context, secondId, secondConsent);
        assertNotNull(first);
        assertNotNull(second);
        try {
            assertNotEquals("Android must not reuse first patient's token", first, second);
            assertEquals("A refreshed snapshot must replace only its own token",
                    second, token(context, secondId, secondConsent));
        } finally {
            first.cancel();
            second.cancel();
        }
    }
}
