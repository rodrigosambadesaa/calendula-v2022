/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.greenrobot.eventbus.EventBus;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.activities.IntakeNotificationMgr;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Fault-injection using synthetic reminders, without real alarms or doses. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class MedicationIntakeReceiverNullSafetyTest {

    @Test
    public void missingReminderDoesNotCrashOrPublishAgendaUpdate() {
        new MedicationIntakeReceiver().onEvent(null, null);
    }

    @Test
    public void missingTimestampReachesUnverifiedNotificationPathWithoutRemovingAlarm() {
        Context context = ApplicationProvider.getApplicationContext();
        EventReminder incomplete = new EventReminder();
        incomplete.setId(78123L);
        EventBus bus = mock(EventBus.class);
        Agenda agenda = mock(Agenda.class);

        try (MockedStatic<IntakeNotificationMgr> notifications =
                     mockStatic(IntakeNotificationMgr.class);
             MockedStatic<CalendulaApp> app = mockStatic(CalendulaApp.class);
             MockedStatic<Agenda> agendaSingleton = mockStatic(Agenda.class)) {
            app.when(CalendulaApp::eventBus).thenReturn(bus);
            agendaSingleton.when(Agenda::instance).thenReturn(agenda);

            new MedicationIntakeReceiver().onEvent(context, incomplete);

            // notify() validates missing timestamps and can show its existing
            // generic 'unverified medication' alert. Do not treat this event
            // as a lost dose or attempt to delete an alarm with no due time.
            notifications.verify(
                    () -> IntakeNotificationMgr.notify(context, incomplete, false));
            verify(bus).post(isA(Agenda.AgendaUpdatedEvent.class));
            verify(agenda, never()).removeReminder(any(Context.class),
                    any(EventReminder.class));
        }
    }
}
