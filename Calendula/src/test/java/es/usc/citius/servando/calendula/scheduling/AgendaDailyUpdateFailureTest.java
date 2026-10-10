/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.j256.ormlite.misc.TransactionManager;
import com.j256.ormlite.support.ConnectionSource;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.sql.SQLException;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.database.DatabaseHelper;
import es.usc.citius.servando.calendula.database.EventReminderDao;
import es.usc.citius.servando.calendula.database.EventInstanceDao;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Fault-injection tests: no real SQLite database, patient or medical data. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AgendaDailyUpdateFailureTest {

    @Test
    public void nestedReminderSqlFailureIsReportedToOuterTransaction() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        DatabaseHelper helper = mock(DatabaseHelper.class);
        ConnectionSource source = mock(ConnectionSource.class);
        when(helper.getConnectionSource()).thenReturn(source);

        try (MockedStatic<DB> db = mockStatic(DB.class);
             MockedStatic<TransactionManager> transactions =
                     mockStatic(TransactionManager.class)) {
            db.when(DB::helper).thenReturn(helper);
            transactions.when(() -> TransactionManager.callInTransaction(
                    org.mockito.ArgumentMatchers.eq(source),
                    org.mockito.ArgumentMatchers.<Callable<Object>>any()))
                    .thenThrow(new SQLException("Synthetic reminder transaction failure"));

            assertFalse("A failed inner transaction must not be treated as success",
                    Agenda.instance().createReminders(context, Collections.emptyList()));
        }
    }

    @Test
    public void failedDailySqlTransactionCannotSetCompletedDate() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        DatabaseHelper helper = mock(DatabaseHelper.class);
        ConnectionSource source = mock(ConnectionSource.class);
        when(helper.getConnectionSource()).thenReturn(source);

        try (MockedStatic<DB> db = mockStatic(DB.class);
             MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class);
             MockedStatic<TransactionManager> transactions =
                     mockStatic(TransactionManager.class)) {
            db.when(DB::helper).thenReturn(helper);
            prefs.when(() -> PreferenceUtils.getString(
                    PreferenceKeys.AGENDA_LAST_UPDATED, null)).thenReturn(null);
            transactions.when(() -> TransactionManager.callInTransaction(
                    org.mockito.ArgumentMatchers.eq(source),
                    org.mockito.ArgumentMatchers.<Callable<Object>>any()))
                    .thenThrow(new SQLException("Synthetic failed daily update"));

            Agenda.instance().onDailyUpdate(context);

            prefs.verify(PreferenceUtils::edit, never());
        }
    }

    @Test
    public void failedAlarmRefreshDoesNotPersistCompletionDate() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        DatabaseHelper helper = mock(DatabaseHelper.class);
        ConnectionSource source = mock(ConnectionSource.class);
        EventReminderDao reminders = mock(EventReminderDao.class);
        when(helper.getConnectionSource()).thenReturn(source);
        when(reminders.findAll()).thenThrow(new IllegalStateException(
                "Synthetic failure reading alarms after commit"));

        try (MockedStatic<DB> db = mockStatic(DB.class);
             MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class);
             MockedStatic<TransactionManager> transactions =
                     mockStatic(TransactionManager.class)) {
            db.when(DB::helper).thenReturn(helper);
            db.when(DB::eventReminders).thenReturn(reminders);
            prefs.when(() -> PreferenceUtils.getString(
                    PreferenceKeys.AGENDA_LAST_UPDATED, null)).thenReturn(null);
            // Simulate a fully committed database transaction. The subsequent
            // alarm refresh must still succeed before the completion marker.
            transactions.when(() -> TransactionManager.callInTransaction(
                    org.mockito.ArgumentMatchers.eq(source),
                    org.mockito.ArgumentMatchers.<Callable<Object>>any()))
                    .thenReturn(null);

            Agenda.instance().onDailyUpdate(context);

            prefs.verify(PreferenceUtils::edit, never());
        }
    }

    @Test
    public void outerCommitFailureAfterNestedReminderWorkNeverMarksDayComplete() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        DatabaseHelper helper = mock(DatabaseHelper.class);
        ConnectionSource source = mock(ConnectionSource.class);
        EventReminderDao reminders = mock(EventReminderDao.class);
        EventInstanceDao events = mock(EventInstanceDao.class);
        when(helper.getConnectionSource()).thenReturn(source);
        when(reminders.findAll()).thenReturn(Collections.emptyList());
        when(events.findAll()).thenReturn(Collections.emptyList());
        AtomicInteger completedTransactions = new AtomicInteger();

        try (MockedStatic<DB> db = mockStatic(DB.class);
             MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class);
             MockedStatic<TransactionManager> transactions =
                     mockStatic(TransactionManager.class)) {
            db.when(DB::helper).thenReturn(helper);
            db.when(DB::eventReminders).thenReturn(reminders);
            db.when(DB::eventInstances).thenReturn(events);
            prefs.when(() -> PreferenceUtils.getString(
                    PreferenceKeys.AGENDA_LAST_UPDATED, null)).thenReturn(null);
            transactions.when(() -> TransactionManager.callInTransaction(
                    org.mockito.ArgumentMatchers.eq(source),
                    org.mockito.ArgumentMatchers.<Callable<Object>>any()))
                    .thenAnswer(invocation -> {
                        // Run the inner reminder work successfully. Then fail
                        // the OUTER transaction after its callable returned,
                        // analogous to a SQLite failure at COMMIT.
                        Object result = ((Callable<?>) invocation.getArgument(1)).call();
                        if (completedTransactions.incrementAndGet() == 2) {
                            throw new SQLException("Synthetic outer COMMIT failure");
                        }
                        return result;
                    });

            Agenda.instance().onDailyUpdate(context);

            assertEquals("Both nested and enclosing SQL callables ran", 2,
                    completedTransactions.get());
            prefs.verify(PreferenceUtils::edit, never());
        }
    }
}
