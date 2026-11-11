/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import java.util.Arrays;

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
    private static String digest(char ch) {
        char[] chars = new char[64];
        Arrays.fill(chars, ch);
        return new String(chars);
    }

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
    public void recycledSqliteIdCannotReusePreviousNotificationButtonToken() {
        Context context = RuntimeEnvironment.getApplication();
        final long rowId = 9242L;
        String originalInstance = java.util.UUID.randomUUID().toString();
        String replacementInstance = java.util.UUID.randomUUID().toString();
        Intent old = new Intent(context, AlarmReceiver.class)
                .putExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT, digest('a'));
        Intent replacement = new Intent(context, AlarmReceiver.class)
                .putExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT, digest('a'));
        PendingIntent oldToken = PendingIntent.getBroadcast(context, (int) rowId,
                IntakeNotificationMgr.identifyNotificationAction(
                        old, "CONFIRM", rowId, originalInstance),
                PendingIntentFlags.immutable(PendingIntent.FLAG_UPDATE_CURRENT));
        PendingIntent currentToken = PendingIntent.getBroadcast(context, (int) rowId,
                IntakeNotificationMgr.identifyNotificationAction(
                        replacement, "CONFIRM", rowId, replacementInstance),
                PendingIntentFlags.immutable(PendingIntent.FLAG_UPDATE_CURRENT));
        try {
            assertNotEquals("A replaced patient must never inherit old action consent",
                    oldToken, currentToken);
            oldToken.cancel();
            assertNotEquals(old.getData(), replacement.getData());
            assertEquals(replacementInstance,
                    replacement.getStringExtra(IntentParams.EXTRA_ALARM_INSTANCE));
        } finally {
            currentToken.cancel();
        }
    }


    @Test
    public void changedDisplayedDoseCannotUpdatePreviousNotificationButtonConsent() {
        Context context = RuntimeEnvironment.getApplication();
        long id = 987600012L;
        String instance = java.util.UUID.randomUUID().toString();
        Intent previous = new Intent(context, AlarmReceiver.class)
                .putExtra(IntentParams.EXTRA_ACTION, IntentParams.ACTION_ALARM_CONFIRM)
                .putExtra(IntentParams.EXTRA_REMINDER_ID, id)
                .putExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT, digest('a'));
        PendingIntent oldToken = PendingIntent.getBroadcast(context, (int) id,
                IntakeNotificationMgr.identifyNotificationAction(
                        previous, "CONFIRM", id, instance),
                PendingIntentFlags.immutable(PendingIntent.FLAG_UPDATE_CURRENT));
        Intent updated = new Intent(context, AlarmReceiver.class)
                .putExtra(IntentParams.EXTRA_ACTION, IntentParams.ACTION_ALARM_CONFIRM)
                .putExtra(IntentParams.EXTRA_REMINDER_ID, id)
                .putExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT, digest('b'));
        PendingIntent newToken = PendingIntent.getBroadcast(context, (int) id,
                IntakeNotificationMgr.identifyNotificationAction(
                        updated, "CONFIRM", id, instance),
                PendingIntentFlags.immutable(PendingIntent.FLAG_UPDATE_CURRENT));
        try {
            assertNotEquals("Updated clinical content must get a distinct Android token",
                    oldToken, newToken);
            assertNotEquals("Consent must be in immutable token identity",
                    previous.getData(), updated.getData());
            assertEquals(oldToken, PendingIntent.getBroadcast(context, (int) id,
                    previous, PendingIntentFlags.immutable(PendingIntent.FLAG_NO_CREATE)));
            assertEquals(newToken, PendingIntent.getBroadcast(context, (int) id,
                    updated, PendingIntentFlags.immutable(PendingIntent.FLAG_NO_CREATE)));

            Intent forged = new Intent(previous);
            forged.putExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT, digest('b'));
            assertEquals("Original token URI is immutable when extras change",
                    previous.getData(), forged.getData());
            assertEquals(null, AlarmReceiver.validatedServiceIntent(context, forged));
        } finally {
            oldToken.cancel();
            newToken.cancel();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void clinicalButtonWithoutDisplayedDoseDigestCannotCreateToken() {
        IntakeNotificationMgr.identifyNotificationAction(
                new Intent(), "CONFIRM", 99L,
                java.util.UUID.randomUUID().toString());
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
