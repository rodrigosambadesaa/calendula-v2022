/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.scheduling.AlarmReceiver;
import es.usc.citius.servando.calendula.util.IntentParams;
import es.usc.citius.servando.calendula.util.PendingIntentFlags;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ReminderNotificationPendingIntentIdentityTest {
    @Test
    public void notificationButtonsForSameReminderAreDistinctAndStable() {
        Context context = RuntimeEnvironment.getApplication();
        final int id = 9012;
        Intent delay = new Intent(context, AlarmReceiver.class);
        delay.putExtra(IntentParams.EXTRA_ACTION, IntentParams.ACTION_ALARM_DELAY);
        delay.putExtra(IntentParams.EXTRA_REMINDER_ID, (long) id);
        Intent cancel = new Intent(context, AlarmReceiver.class);
        cancel.putExtra(IntentParams.EXTRA_ACTION, IntentParams.ACTION_ALARM_CANCEL);
        cancel.putExtra(IntentParams.EXTRA_REMINDER_ID, (long) id);
        PendingIntent firstDelay = PendingIntent.getBroadcast(context, id,
                IntakeNotificationMgr.identifyNotificationAction(delay, "DELAY", id),
                PendingIntentFlags.immutable(PendingIntent.FLAG_UPDATE_CURRENT));
        PendingIntent firstCancel = PendingIntent.getBroadcast(context, id,
                IntakeNotificationMgr.identifyNotificationAction(cancel, "CANCEL", id),
                PendingIntentFlags.immutable(PendingIntent.FLAG_UPDATE_CURRENT));
        assertNotEquals("Cancel and delay must not overwrite each other's extras",
                firstDelay, firstCancel);

        Intent refreshed = new Intent(context, AlarmReceiver.class);
        refreshed.putExtra(IntentParams.EXTRA_ACTION, IntentParams.ACTION_ALARM_DELAY);
        refreshed.putExtra(IntentParams.EXTRA_REMINDER_ID, (long) id);
        refreshed.putExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT, "new-snapshot");
        PendingIntent repeatedDelay = PendingIntent.getBroadcast(context, id,
                IntakeNotificationMgr.identifyNotificationAction(refreshed, "DELAY", id),
                PendingIntentFlags.immutable(PendingIntent.FLAG_UPDATE_CURRENT));
        assertEquals("A re-posted reminder must update an existing pending action",
                firstDelay, repeatedDelay);

        PendingIntent otherReminder = PendingIntent.getBroadcast(context, id + 1,
                IntakeNotificationMgr.identifyNotificationAction(
                        new Intent(context, AlarmReceiver.class), "DELAY", id + 1L),
                PendingIntentFlags.immutable(PendingIntent.FLAG_UPDATE_CURRENT));
        assertNotEquals("Different reminder IDs must not collide",
                firstDelay, otherReminder);
    }

    @Test
    public void reminderIdsThatCollideIn32BitsRemainDistinctPendingIntents() {
        Context context = RuntimeEnvironment.getApplication();
        final long first = 1L;
        final long second = (1L << 32) + 1L;
        assertEquals((int) first, (int) second);
        Intent firstAction = new Intent(context, AlarmReceiver.class);
        firstAction.putExtra(IntentParams.EXTRA_REMINDER_ID, first);
        Intent secondAction = new Intent(context, AlarmReceiver.class);
        secondAction.putExtra(IntentParams.EXTRA_REMINDER_ID, second);
        PendingIntent firstToken = PendingIntent.getBroadcast(context, (int) first,
                IntakeNotificationMgr.identifyNotificationAction(firstAction, "CONFIRM", first),
                PendingIntentFlags.immutable(PendingIntent.FLAG_UPDATE_CURRENT));
        PendingIntent secondToken = PendingIntent.getBroadcast(context, (int) second,
                IntakeNotificationMgr.identifyNotificationAction(secondAction, "CONFIRM", second),
                PendingIntentFlags.immutable(PendingIntent.FLAG_UPDATE_CURRENT));
        assertNotEquals("Full SQL IDs prevent cross-patient action collisions",
                firstToken, secondToken);
        assertNotEquals(firstAction.getData(), secondAction.getData());
    }

    @Test(expected = IllegalArgumentException.class)
    public void unsavedReminderCannotCreateActionablePendingIntent() {
        IntakeNotificationMgr.identifyNotificationAction(new Intent(), "CONFIRM", 0L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void unexpectedNotificationButtonKindIsRejected() {
        IntakeNotificationMgr.identifyNotificationAction(new Intent(), "UNKNOWN", 10L);
    }

    @Test
    public void actionIdentityDoesNotChangeClinicalDispatchExtras() {
        Context context = RuntimeEnvironment.getApplication();
        Intent action = new Intent(context, AlarmReceiver.class);
        action.putExtra(IntentParams.EXTRA_ACTION, IntentParams.ACTION_ALARM_CONFIRM);
        action.putExtra(IntentParams.EXTRA_REMINDER_ID, 19L);
        IntakeNotificationMgr.identifyNotificationAction(action, "CONFIRM", 19L);
        assertEquals(IntentParams.ACTION_ALARM_CONFIRM,
                action.getStringExtra(IntentParams.EXTRA_ACTION));
        assertEquals(19L, action.getLongExtra(IntentParams.EXTRA_REMINDER_ID, -1L));
        assertFalse(action.getAction().isEmpty());
    }
}
