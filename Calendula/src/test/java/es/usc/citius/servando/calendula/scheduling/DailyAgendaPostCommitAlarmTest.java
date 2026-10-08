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
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.database.DatabaseHelper;
import es.usc.citius.servando.calendula.database.EventInstanceDao;
import es.usc.citius.servando.calendula.database.EventReminderDao;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.Collections;
import java.util.concurrent.Callable;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Fault-injection without any real alarm, SQLite file, or patient data. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DailyAgendaPostCommitAlarmTest {

    @Test
    public void dailyTransactionPersistsReminderBeforeCreatingPlatformAlarm()
            throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        DatabaseHelper helper = mock(DatabaseHelper.class);
        ConnectionSource source = mock(ConnectionSource.class);
        EventInstanceDao instances = mock(EventInstanceDao.class);
        EventReminderDao reminders = mock(EventReminderDao.class);
        EventInstance synthetic = mock(EventInstance.class);
        DateTime future = DateTime.now().plusMinutes(15);

        when(helper.getConnectionSource()).thenReturn(source);
        when(synthetic.getTime()).thenReturn(future);
        when(synthetic.getType()).thenReturn(EventType.MEDICATION_INTAKE);
        when(instances.findAll()).thenReturn(Collections.singletonList(synthetic));

        try (MockedStatic<DB> db = mockStatic(DB.class);
             MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class);
             MockedStatic<TransactionManager> transactions =
                     mockStatic(TransactionManager.class)) {
            db.when(DB::helper).thenReturn(helper);
            db.when(DB::eventInstances).thenReturn(instances);
            db.when(DB::eventReminders).thenReturn(reminders);
            prefs.when(() -> PreferenceUtils.getString(
                    PreferenceKeys.SETTINGS_ALARM_REMINDER_WINDOW, "120"))
                    .thenReturn("120");

            transactions.when(() -> TransactionManager.callInTransaction(
                    eq(source), org.mockito.ArgumentMatchers.<Callable<Object>>any()))
                    .thenAnswer(invocation -> {
                        Callable<?> databaseTransaction = invocation.getArgument(1);
                        return databaseTransaction.call();
                    });

            // The DAO assigns no generated ID in this synthetic transaction.
            // Before the fix this path called setAlarm immediately, which
            // attempted PendingIntent creation for an unsaved reminder.
            assertTrue(Agenda.instance().createRemindersForDailyUpdate(context));
            ArgumentCaptor<EventReminder> saved =
                    ArgumentCaptor.forClass(EventReminder.class);
            verify(reminders, times(1)).save(saved.capture());
            assertNotNull(saved.getValue().getDateTime());
            assertNotNull(saved.getValue().getNextTime());
            // No cleanup/AlarmManager access occurs inside this transaction.
            verify(reminders, times(1)).exists(
                    EventType.MEDICATION_INTAKE, future, null);
        }
    }
}
