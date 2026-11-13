/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import android.content.Context;
import android.content.Intent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import java.util.Arrays;

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
    private static String digest(char value) {
        char[] chars = new char[64];
        Arrays.fill(chars, value);
        return new String(chars);
    }

    @Test
    public void oneTapActionForwardsOnlyConsentDigestAndReminderIdentity() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String digest = digest('f');
        String instance = java.util.UUID.randomUUID().toString();
        Intent clicked = new Intent(context, AlarmReceiver.class);
        clicked.putExtra(IntentParams.EXTRA_ACTION, IntentParams.ACTION_ALARM_CONFIRM);
        clicked.putExtra(IntentParams.EXTRA_ALARM_INSTANCE, instance);
        clicked.setData(android.net.Uri.parse(
                "calendula-reminder://notification/731002/" + instance + "/CONFIRM/" + digest));
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
        assertEquals(instance, queued.getStringExtra(IntentParams.EXTRA_ALARM_INSTANCE));
        assertEquals(clicked.getData(), queued.getData());
        assertFalse(queued.hasExtra(IntentParams.EXTRA_PATIENT_ID));
        assertFalse(queued.hasExtra(IntentParams.EXTRA_DATETIME));
    }

    @Test
    public void cancellationAndDelayOnlyQueueRecordedDoseSnapshot() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String shown = digest('a');
        for (String action : Arrays.asList(
                IntentParams.ACTION_ALARM_CANCEL, IntentParams.ACTION_ALARM_DELAY)) {
            Intent clicked = new Intent(context, AlarmReceiver.class);
            clicked.putExtra(IntentParams.EXTRA_ACTION, action);
            clicked.putExtra(IntentParams.EXTRA_REMINDER_ID, 731002L);
            assertNull("Legacy clinical actions are not actionable",
                    AlarmReceiver.validatedServiceIntent(context, clicked));
            clicked.putExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT, "invalid");
            assertNull(AlarmReceiver.validatedServiceIntent(context, clicked));
            clicked.putExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT, shown);
            clicked.putExtra(IntentParams.EXTRA_PATIENT_ID, 731003L);
            String instance = java.util.UUID.randomUUID().toString();
            String kind = action.equals(IntentParams.ACTION_ALARM_DELAY) ? "DELAY" : "CANCEL";
            clicked.putExtra(IntentParams.EXTRA_ALARM_INSTANCE, instance);
            clicked.setData(android.net.Uri.parse(
                    "calendula-reminder://notification/731002/" + instance + "/" + kind + "/" + shown));
            Intent work = AlarmReceiver.validatedServiceIntent(context, clicked);
            assertNotNull(work);
            assertEquals(action, work.getStringExtra(IntentParams.EXTRA_ACTION));
            assertEquals(shown,
                    work.getStringExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT));
            assertFalse(work.hasExtra(IntentParams.EXTRA_PATIENT_ID));
            assertEquals(instance, work.getStringExtra(IntentParams.EXTRA_ALARM_INSTANCE));
        }
    }


    @Test
    public void changedActionExtrasCannotInheritNewConsentFromOlderAndroidToken() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String original = digest('a');
        String updated = digest('b');
        String instance = java.util.UUID.randomUUID().toString();
        Intent oldClick = new Intent(context, AlarmReceiver.class);
        oldClick.putExtra(IntentParams.EXTRA_ACTION, IntentParams.ACTION_ALARM_CONFIRM);
        oldClick.putExtra(IntentParams.EXTRA_REMINDER_ID, 731004L);
        oldClick.putExtra(IntentParams.EXTRA_ALARM_INSTANCE, instance);
        oldClick.putExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT, original);
        oldClick.setData(android.net.Uri.parse(
                "calendula-reminder://notification/731004/" + instance
                        + "/CONFIRM/" + original));
        assertNotNull(AlarmReceiver.validatedServiceIntent(context, oldClick));

        // PendingIntent.FLAG_UPDATE_CURRENT may mutate extras, not identity.
        // The original Android token must not silently authorize new doses.
        oldClick.putExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT, updated);
        assertNull(AlarmReceiver.validatedServiceIntent(context, oldClick));
        oldClick.putExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT, original);
        assertNotNull(AlarmReceiver.validatedServiceIntent(context, oldClick));
        oldClick.setData(android.net.Uri.parse(
                "calendula-reminder://notification/731004/" + instance + "/CONFIRM"));
        assertNull("Legacy ID/UUID-only clinical actions must fail closed",
                AlarmReceiver.validatedServiceIntent(context, oldClick));
    }

    @Test
    public void oldOrMalformedNotificationCannotQueueAConfirmation() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent clicked = new Intent(context, AlarmReceiver.class);
        clicked.putExtra(IntentParams.EXTRA_ACTION, IntentParams.ACTION_ALARM_CONFIRM);
        clicked.putExtra(IntentParams.EXTRA_REMINDER_ID, 731002L);
        assertNull(AlarmReceiver.validatedServiceIntent(context, clicked));

        clicked.putExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT, digest('g'));
        assertNull(AlarmReceiver.validatedServiceIntent(context, clicked));

        clicked.putExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT, digest('d'));
        clicked.putExtra(IntentParams.EXTRA_REMINDER_ID, 0L);
        assertNull(AlarmReceiver.validatedServiceIntent(context, clicked));
    }
}
