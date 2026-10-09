/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import android.content.Context;

import com.j256.ormlite.misc.TransactionManager;
import com.j256.ormlite.support.ConnectionSource;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.sql.SQLException;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.database.DatabaseHelper;
import es.usc.citius.servando.calendula.database.EventReminderDao;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Synthetic SQL-commit injection; no patient, SQLite file or platform alarm. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PublicReminderCommitBoundaryTest {

    @Test
    public void sqlCommitFailureNeverReachesPlatformAlarmServices() throws Exception {
        Context context = mock(Context.class);
        DatabaseHelper helper = mock(DatabaseHelper.class);
        ConnectionSource source = mock(ConnectionSource.class);
        EventReminderDao reminders = mock(EventReminderDao.class);
        EventInstance event = mock(EventInstance.class);
        DateTime future = DateTime.now().plusMinutes(3);

        when(helper.getConnectionSource()).thenReturn(source);
        when(event.getType()).thenReturn(EventType.MEDICATION_INTAKE);
        when(event.getTime()).thenReturn(future);

        try (MockedStatic<DB> db = mockStatic(DB.class);
             MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class);
             MockedStatic<TransactionManager> transactions = mockStatic(TransactionManager.class)) {
            db.when(DB::helper).thenReturn(helper);
            db.when(DB::eventReminders).thenReturn(reminders);
            prefs.when(() -> PreferenceUtils.getString(
                    PreferenceKeys.SETTINGS_ALARM_REMINDER_WINDOW, "120"))
                    .thenReturn("120");

            transactions.when(() -> TransactionManager.callInTransaction(
                    eq(source), org.mockito.ArgumentMatchers.<Callable<Object>>any()))
                    .thenAnswer(invocation -> {
                        // The transaction body has fully executed and persisted
                        // its synthetic write before COMMIT itself now fails.
                        Callable<?> work = invocation.getArgument(1);
                        work.call();
                        throw new SQLException("Synthetic SQLite COMMIT failure");
                    });

            assertFalse("A failed commit must not report reminder creation success",
                    Agenda.instance().createReminders(context,
                            Collections.singletonList(event)));
            verify(reminders, times(1)).save(any(EventReminder.class));
            verify(reminders, never()).findAll();
            verifyNoInteractions(context);
        }
    }

    @Test
    public void publicReconciliationReadsRemindersOnlyAfterSqlCommit() throws Exception {
        Context context = mock(Context.class);
        DatabaseHelper helper = mock(DatabaseHelper.class);
        ConnectionSource source = mock(ConnectionSource.class);
        EventReminderDao reminders = mock(EventReminderDao.class);
        EventInstance event = mock(EventInstance.class);
        DateTime future = DateTime.now().plusMinutes(4);
        AtomicBoolean committed = new AtomicBoolean(false);
        when(helper.getConnectionSource()).thenReturn(source);
        when(event.getType()).thenReturn(EventType.MEDICATION_INTAKE);
        when(event.getTime()).thenReturn(future);
        when(reminders.findAll()).thenAnswer(invocation -> {
            assertTrue("Platform reconciliation must occur after SQL COMMIT", committed.get());
            return Collections.emptyList();
        });
        // Emulate generated SQLite primary key without a real database.
        doAnswer(invocation -> {
            EventReminder persisted = invocation.getArgument(0);
            persisted.setId(9000002451L);
            return null;
        }).when(reminders).save(any(EventReminder.class));
        when(reminders.findById(9000002451L)).thenAnswer(invocation -> {
            assertTrue("New reminder must be re-read only after SQL COMMIT", committed.get());
            return null; // Simulate removal by the post-commit cleanup.
        });

        try (MockedStatic<DB> db = mockStatic(DB.class);
             MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class);
             MockedStatic<TransactionManager> transactions = mockStatic(TransactionManager.class)) {
            db.when(DB::helper).thenReturn(helper);
            db.when(DB::eventReminders).thenReturn(reminders);
            prefs.when(() -> PreferenceUtils.getString(
                    PreferenceKeys.SETTINGS_ALARM_REMINDER_WINDOW, "120"))
                    .thenReturn("120");

            transactions.when(() -> TransactionManager.callInTransaction(
                    eq(source), org.mockito.ArgumentMatchers.<Callable<Object>>any()))
                    .thenAnswer(invocation -> {
                        Callable<?> work = invocation.getArgument(1);
                        Object result = work.call();
                        committed.set(true);
                        return result;
                    });

            assertTrue(Agenda.instance().createReminders(context,
                    Collections.singletonList(event)));
            assertTrue(committed.get());
            verify(reminders, times(1)).save(any(EventReminder.class));
            // Only a cleanup scan is allowed. Never re-arm all stale
            // reminders; individually verify newly persisted IDs instead.
            verify(reminders, times(1)).findAll();
            verify(reminders, times(1)).findById(9000002451L);
            verifyNoInteractions(context);
        }
    }
}
