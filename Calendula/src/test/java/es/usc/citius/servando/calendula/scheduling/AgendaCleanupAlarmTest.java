/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import android.app.PendingIntent;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;

import es.usc.citius.servando.calendula.activities.IntakeNotificationMgr;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.database.EventInstanceDao;
import es.usc.citius.servando.calendula.database.EventReminderDao;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;
import es.usc.citius.servando.calendula.util.PendingIntentFlags;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Synthetic alarms only; no real medicines or SQLite records are modified. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AgendaCleanupAlarmTest {

    private EventReminder reminder(long id) {
        EventReminder result = new EventReminder(
                DateTime.now().plusHours(2), EventType.MEDICATION_INTAKE);
        result.setId(id);
        result.setNextTime(result.getDateTime());
        return result;
    }

    private PendingIntent existing(Context context, EventReminder reminder) {
        return PendingIntent.getBroadcast(context, 0,
                Agenda.reminderBroadcastIntent(context, reminder),
                PendingIntentFlags.immutable(PendingIntent.FLAG_NO_CREATE));
    }

    private PendingIntent create(Context context, EventReminder reminder) {
        return PendingIntent.getBroadcast(context, 0,
                Agenda.reminderBroadcastIntent(context, reminder),
                PendingIntentFlags.immutable(PendingIntent.FLAG_UPDATE_CURRENT));
    }

    @Test
    public void staleReminderAlreadyRemovedIsANoOp() {
        Context context = ApplicationProvider.getApplicationContext();
        EventInstanceDao events = mock(EventInstanceDao.class);
        EventReminderDao reminders = mock(EventReminderDao.class);
        DateTime time = DateTime.now().plusHours(1);
        try (MockedStatic<DB> db = mockStatic(DB.class);
             MockedStatic<IntakeNotificationMgr> notifications =
                     mockStatic(IntakeNotificationMgr.class)) {
            db.when(DB::eventInstances).thenReturn(events);
            db.when(DB::eventReminders).thenReturn(reminders);
            // A null findBy result means the record was already removed.
            Agenda.instance().cleanReminderIfPossible(
                    context, null, EventType.MEDICATION_INTAKE, time);
            verify(reminders, never()).remove(org.mockito.ArgumentMatchers.nullable(EventReminder.class));
            notifications.verifyNoInteractions();
        }
    }

    @Test
    public void cleaningExpiredReminderRevokesPendingIntentAndKeepsDaoConsistent() {
        Context context = ApplicationProvider.getApplicationContext();
        EventInstanceDao events = mock(EventInstanceDao.class);
        EventReminderDao reminders = mock(EventReminderDao.class);
        EventReminder r = reminder(9000001201L);
        when(reminders.findBy(r.getEventType(), r.getDateTime(), null)).thenReturn(r);
        PendingIntent token = create(context, r);
        assertNotNull(existing(context, r));
        try (MockedStatic<DB> db = mockStatic(DB.class);
             MockedStatic<IntakeNotificationMgr> notifications =
                     mockStatic(IntakeNotificationMgr.class)) {
            db.when(DB::eventInstances).thenReturn(events);
            db.when(DB::eventReminders).thenReturn(reminders);
            Agenda.instance().cleanReminderIfPossible(
                    context, null, r.getEventType(), r.getDateTime());
            assertNull("Removing reminder must also revoke Android token", existing(context, r));
            verify(reminders).remove(r);
            notifications.verify(() -> IntakeNotificationMgr.cancel(context, r));
        } finally {
            token.cancel();
        }
    }

    @Test
    public void bulkDeleteRevokesAllScheduledAndroidTokens() {
        Context context = ApplicationProvider.getApplicationContext();
        EventReminderDao reminders = mock(EventReminderDao.class);
        EventReminder a = reminder(9000001202L);
        EventReminder b = reminder(9000001203L);
        when(reminders.findAll()).thenReturn(Arrays.asList(a, b));
        PendingIntent first = create(context, a);
        PendingIntent second = create(context, b);
        try (MockedStatic<DB> db = mockStatic(DB.class);
             MockedStatic<IntakeNotificationMgr> notifications =
                     mockStatic(IntakeNotificationMgr.class)) {
            db.when(DB::eventReminders).thenReturn(reminders);
            Agenda.instance().deleteAllReminders(context);
            assertNull(existing(context, a));
            assertNull(existing(context, b));
            verify(reminders).remove(a);
            verify(reminders).remove(b);
        } finally {
            first.cancel();
            second.cancel();
        }
    }
}
