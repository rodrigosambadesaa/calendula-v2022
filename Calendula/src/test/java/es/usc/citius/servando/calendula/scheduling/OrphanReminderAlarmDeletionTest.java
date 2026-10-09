/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import android.app.PendingIntent;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.database.EventInstanceDao;
import es.usc.citius.servando.calendula.database.EventReminderDao;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;
import es.usc.citius.servando.calendula.util.PendingIntentFlags;

import org.joda.time.DateTime;
import java.sql.SQLException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Synthetic orphan reminders only, no persistent patient database. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class OrphanReminderAlarmDeletionTest {

    private static EventReminder orphan(long id) {
        EventReminder r = new EventReminder(
                DateTime.now().plusHours(2), EventType.MEDICATION_INTAKE);
        r.setId(id);
        r.setNextTime(r.getDateTime());
        return r;
    }

    private static PendingIntent lookup(Context context, EventReminder r, int flags) {
        return PendingIntent.getBroadcast(context, 0,
                Agenda.reminderBroadcastIntent(context, r),
                PendingIntentFlags.immutable(flags));
    }

    @Test
    public void failedSqliteDeleteLeavesAndroidAlarmRegistered() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        EventReminder r = orphan(9000001601L);
        EventReminderDao reminders = mock(EventReminderDao.class);
        EventInstanceDao instances = mock(EventInstanceDao.class);
        when(reminders.findById(r.getId())).thenReturn(r);
        when(reminders.delete(r)).thenThrow(new SQLException("Synthetic SQLite failure"));
        PendingIntent original = lookup(context, r, PendingIntent.FLAG_UPDATE_CURRENT);
        try (MockedStatic<DB> db = mockStatic(DB.class)) {
            db.when(DB::eventReminders).thenReturn(reminders);
            db.when(DB::eventInstances).thenReturn(instances);

            try {
                Agenda.instance().onReceiveAlarm(context, r.getId());
                fail("SQLite deletion failure should propagate for retry");
            } catch (IllegalStateException expected) {
                // Retain the alarm: the reminder row has not been deleted.
            }
            assertNotNull("A surviving DB reminder must retain its OS token",
                    lookup(context, r, PendingIntent.FLAG_NO_CREATE));
            verify(reminders).delete(r);
        } finally {
            original.cancel();
        }
    }

    @Test
    public void successfulSqliteDeleteCancelsObsoleteAlarm() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        EventReminder r = orphan(9000001602L);
        EventReminderDao reminders = mock(EventReminderDao.class);
        EventInstanceDao instances = mock(EventInstanceDao.class);
        when(reminders.findById(r.getId())).thenReturn(r);
        when(reminders.delete(r)).thenReturn(1);
        PendingIntent original = lookup(context, r, PendingIntent.FLAG_UPDATE_CURRENT);
        try (MockedStatic<DB> db = mockStatic(DB.class)) {
            db.when(DB::eventReminders).thenReturn(reminders);
            db.when(DB::eventInstances).thenReturn(instances);
            Agenda.instance().onReceiveAlarm(context, r.getId());
            verify(reminders).delete(r);
            assertNull("A deleted orphan must not leave an OS alarm token",
                    lookup(context, r, PendingIntent.FLAG_NO_CREATE));
        } finally {
            original.cancel();
        }
    }

    @Test
    public void zeroSqliteRowsDeletedMustNotCancelPersistedAlarmToken() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        EventReminder r = orphan(9000001603L);
        EventReminderDao reminders = mock(EventReminderDao.class);
        EventInstanceDao instances = mock(EventInstanceDao.class);
        when(reminders.findById(r.getId())).thenReturn(r);
        when(reminders.delete(r)).thenReturn(0);
        PendingIntent original = lookup(context, r, PendingIntent.FLAG_UPDATE_CURRENT);
        try (MockedStatic<DB> db = mockStatic(DB.class)) {
            db.when(DB::eventReminders).thenReturn(reminders);
            db.when(DB::eventInstances).thenReturn(instances);
            try {
                Agenda.instance().onReceiveAlarm(context, r.getId());
                fail("A zero-row SQLite delete must not be treated as success");
            } catch (IllegalStateException expected) {
                // No matching row was deleted; do not actively cancel the token.
            }
            verify(reminders).delete(r);
            assertNotNull("A zero-row delete must preserve the PendingIntent token",
                    lookup(context, r, PendingIntent.FLAG_NO_CREATE));
        } finally {
            original.cancel();
        }
    }
}
