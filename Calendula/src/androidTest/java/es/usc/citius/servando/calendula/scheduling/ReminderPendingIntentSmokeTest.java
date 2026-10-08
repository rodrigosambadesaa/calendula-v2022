/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import android.app.PendingIntent;
import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.util.IntentParams;
import org.joda.time.DateTime;
import es.usc.citius.servando.calendula.util.PendingIntentFlags;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Real Android PendingIntent identity checks using synthetic unscheduled tokens.
 * Does not post notifications, send broadcasts or touch the medical database.
 */
@RunWith(AndroidJUnit4.class)
public class ReminderPendingIntentSmokeTest {

    private static PendingIntent token(Context context, EventReminder reminder) {
        return PendingIntent.getBroadcast(context, 0,
                Agenda.reminderBroadcastIntent(context, reminder),
                PendingIntentFlags.immutable(PendingIntent.FLAG_UPDATE_CURRENT));
    }

    @Test
    public void persistedIdKeepsSameTokenAcrossMetadataChanges() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        EventReminder reminder = new EventReminder();
        reminder.setId(9000000001L);
        PendingIntent before = token(context, reminder);
        try {
            reminder.setAutoRepeat(true);
            PendingIntent after = token(context, reminder);
            assertEquals("Mutable reminder fields must not change broadcast token",
                    before, after);
        } finally {
            before.cancel();
        }
    }

    @Test
    public void cancellationStillFindsAlarmAfterReminderMetadataChanges() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        EventReminder reminder = new EventReminder();
        reminder.setId(9000000004L);
        DateTime future = DateTime.now().plusHours(2);
        reminder.setDateTime(future);
        reminder.setNextTime(future);
        try {
            Agenda.instance().setAlarm(context, reminder);
            assertNotNull("Scheduled alarm token should be registered",
                    PendingIntent.getBroadcast(context, 0,
                            Agenda.reminderBroadcastIntent(context, reminder),
                            PendingIntentFlags.immutable(PendingIntent.FLAG_NO_CREATE)));

            // Former request-code identity changed with this property.
            reminder.setAutoRepeat(true);
            Agenda.instance().cancelAlarm(context, reminder);
            assertNull("Cancelling reminder must revoke stable alarm token",
                    PendingIntent.getBroadcast(context, 0,
                            Agenda.reminderBroadcastIntent(context, reminder),
                            PendingIntentFlags.immutable(PendingIntent.FLAG_NO_CREATE)));
        } finally {
            Agenda.instance().cancelAlarm(context, reminder);
        }
    }

    @Test
    public void legacyHashBasedTokenIsRetiredWhenReminderIsRescheduled() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        EventReminder reminder = new EventReminder();
        reminder.setId(9000000005L);
        DateTime future = DateTime.now().plusHours(2);
        reminder.setDateTime(future);
        reminder.setNextTime(future);

        android.content.Intent legacy = new android.content.Intent(context, AlarmReceiver.class);
        legacy.putExtra(IntentParams.EXTRA_ACTION, IntentParams.ACTION_ALARM_REMINDER);
        legacy.putExtra(IntentParams.EXTRA_REMINDER_ID, reminder.getId());
        PendingIntent legacyToken = PendingIntent.getBroadcast(context,
                reminder.hashCode(), legacy,
                PendingIntentFlags.immutable(PendingIntent.FLAG_UPDATE_CURRENT));
        try {
            Agenda.instance().setAlarm(context, reminder);
            assertNull("Old request-code token should be retired on upgrade",
                    PendingIntent.getBroadcast(context, reminder.hashCode(), legacy,
                            PendingIntentFlags.immutable(PendingIntent.FLAG_NO_CREATE)));
        } finally {
            legacyToken.cancel();
            Agenda.instance().cancelAlarm(context, reminder);
        }
    }

    @Test
    public void distinctReminderIdsProduceDistinctPendingIntents() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        EventReminder a = new EventReminder();
        a.setId(9000000002L);
        EventReminder b = new EventReminder();
        b.setId(9000000003L);
        PendingIntent first = token(context, a);
        PendingIntent second = token(context, b);
        try {
            assertFalse("Different reminders must never reuse a PendingIntent",
                    first.equals(second));
        } finally {
            first.cancel();
            second.cancel();
        }
    }
}
