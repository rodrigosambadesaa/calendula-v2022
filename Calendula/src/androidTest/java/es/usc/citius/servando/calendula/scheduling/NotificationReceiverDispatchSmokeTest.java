/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import android.content.Context;
import android.content.Intent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.util.IntentParams;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Real Android Intent/Bundle serialization on supported emulator APIs.
 * No patient data, SQLite mutations, broadcasts or OS alarms.
 */
@RunWith(AndroidJUnit4.class)
public class NotificationReceiverDispatchSmokeTest {

    @Test
    public void oneTapActionForwardsOnlyConsentDigestAndReminderIdentity() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String digest = "f9".repeat(32);
        Intent clicked = new Intent(context, AlarmReceiver.class);
        clicked.putExtra(IntentParams.EXTRA_ACTION, IntentParams.ACTION_ALARM_CONFIRM);
        clicked.putExtra(IntentParams.EXTRA_REMINDER_ID, 731002L);
        clicked.putExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT, digest);
        clicked.putExtra(IntentParams.EXTRA_PATIENT_ID, 731003L);
        clicked.putExtra(IntentParams.EXTRA_DATETIME, "synthetic sensitive text");

        Intent queued = AlarmReceiver.validatedServiceIntent(context, clicked);
        assertNotNull(queued);
        assertEquals(AlarmIntentService.class.getName(), queued.getComponent().getClassName());
        assertEquals(IntentParams.ACTION_ALARM_CONFIRM,
                queued.getStringExtra(IntentParams.EXTRA_ACTION));
        assertEquals(731002L, queued.getLongExtra(IntentParams.EXTRA_REMINDER_ID, -1L));
        assertEquals(digest, queued.getStringExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT));
        assertFalse(queued.hasExtra(IntentParams.EXTRA_PATIENT_ID));
        assertFalse(queued.hasExtra(IntentParams.EXTRA_DATETIME));
    }

    @Test
    public void oldOrMalformedNotificationCannotQueueAConfirmation() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent clicked = new Intent(context, AlarmReceiver.class);
        clicked.putExtra(IntentParams.EXTRA_ACTION, IntentParams.ACTION_ALARM_CONFIRM);
        clicked.putExtra(IntentParams.EXTRA_REMINDER_ID, 731002L);
        assertNull(AlarmReceiver.validatedServiceIntent(context, clicked));

        clicked.putExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT, "g".repeat(64));
        assertNull(AlarmReceiver.validatedServiceIntent(context, clicked));

        clicked.putExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT, "d".repeat(64));
        clicked.putExtra(IntentParams.EXTRA_REMINDER_ID, 0L);
        assertNull(AlarmReceiver.validatedServiceIntent(context, clicked));
    }
}
