/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
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

import java.sql.SQLException;
import java.util.Collections;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.database.EventInstanceDao;
import es.usc.citius.servando.calendula.database.EventReminderDao;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;
import es.usc.citius.servando.calendula.util.PendingIntentFlags;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Synthetic reminder identity and SQLite failure injection; no real patient data. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class VerifiedAlarmCleanupSqlFailureTest {
    private static EventReminder reminder(long id) {
        DateTime time = DateTime.now().plusHours(17);
        EventReminder r = new EventReminder(time, EventType.MEDICATION_INTAKE);
        r.setNextTime(time);
        r.setId(id);
        return r;
    }

    private static PendingIntent token(Context context, EventReminder r, int flags) {
        return PendingIntent.getBroadcast(context, 0,
                Agenda.reminderBroadcastIntent(context, r),
                PendingIntentFlags.immutable(flags));
    }

    @Test
    public void zeroRowCleanupMustKeepTokenIfSqliteRowSurvives() throws Exception {
        exerciseFailure(false, false);
    }

    @Test
    public void sqlExceptionCleanupMustKeepTokenIfSqliteRowSurvives() throws Exception {
        exerciseFailure(true, false);
    }

    @Test
    public void zeroRowDeleteAllMustKeepTokenIfSqliteRowSurvives() throws Exception {
        exerciseFailure(false, true);
    }

    private void exerciseFailure(boolean sqlException, boolean deleteAll) throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        EventReminder r = reminder(sqlException ? 9000002901L
                : (deleteAll ? 9000002902L : 9000002903L));
        EventReminderDao reminders = mock(EventReminderDao.class);
        EventInstanceDao instances = mock(EventInstanceDao.class);
        when(reminders.findById(r.getId())).thenReturn(r);
        when(reminders.findBy(r.getEventType(), r.getDateTime(), null)).thenReturn(r);
        when(reminders.findAll()).thenReturn(Collections.singletonList(r));
        if (sqlException) {
            when(reminders.delete(r)).thenThrow(new SQLException("Synthetic SQLite failure"));
        } else {
            when(reminders.delete(r)).thenReturn(0);
        }

        PendingIntent original = token(context, r, PendingIntent.FLAG_UPDATE_CURRENT);
        try (MockedStatic<DB> db = mockStatic(DB.class)) {
            db.when(DB::eventReminders).thenReturn(reminders);
            db.when(DB::eventInstances).thenReturn(instances);
            try {
                if (deleteAll) {
                    Agenda.instance().deleteAllReminders(context);
                } else {
                    Agenda.instance().cleanReminderIfPossible(
                            context, null, r.getEventType(), r.getDateTime());
                }
                fail("An unremoved SQLite reminder must not be treated as deleted");
            } catch (IllegalStateException expected) {
                // SQLite is authoritative. Any surviving row retains its token.
            }
            verify(reminders).delete(r);
            assertNotNull("A still-persisted reminder must retain its Android token",
                    token(context, r, PendingIntent.FLAG_NO_CREATE));
        } finally {
            original.cancel();
        }
    }

    @Test
    public void alreadyAbsentRowWithZeroCountAllowsCleanup() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        EventReminder r = reminder(9000002904L);
        EventReminderDao reminders = mock(EventReminderDao.class);
        EventInstanceDao instances = mock(EventInstanceDao.class);
        when(reminders.findBy(r.getEventType(), r.getDateTime(), null)).thenReturn(r);
        when(reminders.findById(r.getId())).thenReturn(null);
        when(reminders.delete(r)).thenReturn(0);

        PendingIntent original = token(context, r, PendingIntent.FLAG_UPDATE_CURRENT);
        try (MockedStatic<DB> db = mockStatic(DB.class)) {
            db.when(DB::eventReminders).thenReturn(reminders);
            db.when(DB::eventInstances).thenReturn(instances);
            Agenda.instance().cleanReminderIfPossible(
                    context, null, r.getEventType(), r.getDateTime());
            verify(reminders).delete(r);
            assertNull("Verified-absent rows must not retain orphan OS tokens",
                    token(context, r, PendingIntent.FLAG_NO_CREATE));
        } finally {
            original.cancel();
        }
    }
}
