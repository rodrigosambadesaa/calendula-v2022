/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.util.IntentParams;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Offline identity tests: no alarms are actually delivered or persisted. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AgendaReminderIntentIdentityTest {

    @Test
    public void reminderIntentIdentitySurvivesChangesToMutableFields() {
        Context context = ApplicationProvider.getApplicationContext();
        EventReminder reminder = new EventReminder();
        reminder.setId(42L);

        int oldHash = reminder.hashCode();
        Intent before = Agenda.reminderBroadcastIntent(context, reminder);
        reminder.setAutoRepeat(true);
        int newHash = reminder.hashCode();
        Intent after = Agenda.reminderBroadcastIntent(context, reminder);

        assertTrue("The legacy mutable hash must demonstrably change", oldHash != newHash);
        assertTrue("One persisted reminder must retain one alarm identity",
                before.filterEquals(after));
        assertEquals(IntentParams.ACTION_ALARM_REMINDER, after.getAction());
        assertEquals(Long.valueOf(42L),
                Long.valueOf(after.getLongExtra(IntentParams.EXTRA_REMINDER_ID, -1)));
    }

    @Test
    public void distinctReminderIdsCannotAliasOrCollideWithDailyUpdateIntent() {
        Context context = ApplicationProvider.getApplicationContext();
        EventReminder first = new EventReminder();
        first.setId(10001L);
        EventReminder second = new EventReminder();
        second.setId(10002L);

        Intent a = Agenda.reminderBroadcastIntent(context, first);
        Intent b = Agenda.reminderBroadcastIntent(context, second);
        Intent daily = new Intent(context, AlarmReceiver.class);
        daily.putExtra(IntentParams.EXTRA_ACTION, IntentParams.ACTION_DAILY_UPDATE);

        assertFalse("Different medication reminders must not share an alarm",
                a.filterEquals(b));
        assertFalse("Daily update must not replace medication reminder",
                a.filterEquals(daily));
    }

    @Test
    public void unsavedReminderCannotBeScheduledUnderAnUnstableIdentity() {
        Context context = ApplicationProvider.getApplicationContext();
        try {
            Agenda.reminderBroadcastIntent(context, new EventReminder());
            fail("Reminders require a database ID before being scheduled");
        } catch (IllegalArgumentException expected) {
            // No pending intent created for an unsaved ORM record.
        }
    }
}
