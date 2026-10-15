/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import android.content.Context;
import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.util.IntentParams;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

/** Corrupt queued jobs must not query SQLite or update any clinical data. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class QueuedAlarmJobValidationTest {

    private static Intent work(Context context, String action, long reminderId) {
        return new Intent(context, AlarmIntentService.class)
                .putExtra(IntentParams.EXTRA_ACTION, action)
                .putExtra(IntentParams.EXTRA_REMINDER_ID, reminderId);
    }

    @Test
    public void legacyJobsWithNoConsentAreRejectedBeforeAnyDatabaseRead() {
        Context context = RuntimeEnvironment.getApplication();
        AlarmIntentService service = Robolectric.buildService(
                AlarmIntentService.class).create().get();

        Intent noSnapshot = work(context, IntentParams.ACTION_ALARM_CONFIRM, 100L);
        Intent malformedSnapshot = work(context, IntentParams.ACTION_ALARM_CANCEL, 101L)
                .putExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT, "not a digest");
        Intent invalidReminder = work(context, IntentParams.ACTION_ALARM_DELAY, -1L)
                .putExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT, new String(new char[64]).replace((char) 0, 'a'));
        Intent invalidType = work(context, IntentParams.ACTION_ALARM_CONFIRM, 102L)
                .putExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT, 7);
        Intent unknown = work(context, "unsupported legacy action", 103L);
        Intent incompatibleAction = new Intent(context, AlarmIntentService.class)
                .putExtra(IntentParams.EXTRA_ACTION, 123)
                .putExtra(IntentParams.EXTRA_REMINDER_ID, 100L);

        try (MockedStatic<DB> db = mockStatic(DB.class);
             MockedStatic<Agenda> agenda = mockStatic(Agenda.class)) {
            service.onHandleWork(noSnapshot);
            service.onHandleWork(malformedSnapshot);
            service.onHandleWork(invalidReminder);
            service.onHandleWork(invalidType);
            service.onHandleWork(unknown);
            service.onHandleWork(incompatibleAction);
            db.verifyNoInteractions();
            agenda.verifyNoInteractions();
        }
    }

    @Test
    public void validQueuedReminderStillReachesAgendaAfterValidation() {
        Context context = RuntimeEnvironment.getApplication();
        AlarmIntentService service = Robolectric.buildService(
                AlarmIntentService.class).create().get();
        Agenda agenda = mock(Agenda.class);
        try (MockedStatic<Agenda> agendaSingleton = mockStatic(Agenda.class)) {
            agendaSingleton.when(Agenda::instance).thenReturn(agenda);
            service.onHandleWork(work(context, IntentParams.ACTION_ALARM_REMINDER, 99L));
            verify(agenda).onReceiveAlarm(service, 99L);
        }
    }

    @Test
    public void validDailyRefreshStillReachesAgendaAfterValidation() {
        Context context = RuntimeEnvironment.getApplication();
        AlarmIntentService service = Robolectric.buildService(
                AlarmIntentService.class).create().get();
        Agenda agenda = mock(Agenda.class);
        try (MockedStatic<Agenda> agendaSingleton = mockStatic(Agenda.class)) {
            agendaSingleton.when(Agenda::instance).thenReturn(agenda);
            service.onHandleWork(new Intent(context, AlarmIntentService.class)
                    .putExtra(IntentParams.EXTRA_ACTION, IntentParams.ACTION_DAILY_UPDATE));
            verify(agenda).onDailyUpdate(service);
        }
    }

    @Test
    public void validQueuedSnoozeKeepsExactlyTheRecordedDoseSnapshot() {
        Context context = RuntimeEnvironment.getApplication();
        AlarmIntentService service = Robolectric.buildService(
                AlarmIntentService.class).create().get();
        Agenda agenda = mock(Agenda.class);
        String consent = new String(new char[64]).replace((char) 0, 'b');
        try (MockedStatic<Agenda> agendaSingleton = mockStatic(Agenda.class)) {
            agendaSingleton.when(Agenda::instance).thenReturn(agenda);
            service.onHandleWork(work(context, IntentParams.ACTION_ALARM_DELAY, 123L)
                    .putExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT, consent));
            // Mockito defaults Boolean return values to false, so the
            // synthetic test does not post a toast or mutate SQLite.
            verify(agenda).delayReminderWithSnapshot(service, 123L, consent);
        }
    }
}
