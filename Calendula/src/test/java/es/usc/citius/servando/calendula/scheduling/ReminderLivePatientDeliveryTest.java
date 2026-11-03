/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import android.content.Context;
import android.app.PendingIntent;

import androidx.test.core.app.ApplicationProvider;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.database.PatientDao;
import es.usc.citius.servando.calendula.database.EventReminderDao;
import es.usc.citius.servando.calendula.database.EventInstanceDao;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;
import es.usc.citius.servando.calendula.util.PendingIntentFlags;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** All examples are synthetic; no real medication or patient database. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ReminderLivePatientDeliveryTest {
    private static Patient owner(Long id) {
        Patient p = new Patient();
        p.setId(id);
        return p;
    }

    private static EventReminder reminder(Long patientId) {
        DateTime when = DateTime.now().minusMinutes(2);
        EventReminder reminder = new EventReminder(when, EventType.MEDICATION_INTAKE);
        reminder.setId(7800981L);
        reminder.setPatient(patientId == null ? null : owner(patientId));
        reminder.setNextTime(when);
        return reminder;
    }

    @Test
    public void validOwnerMustStillExistInCurrentSqliteSnapshot() {
        EventReminder r = reminder(45L);
        PatientDao patients = mock(PatientDao.class);
        try (MockedStatic<DB> db = mockStatic(DB.class)) {
            db.when(DB::patients).thenReturn(patients);
            when(patients.findById(45L)).thenReturn(owner(45L));
            assertTrue(Agenda.hasLiveReminderOwner(r));
            when(patients.findById(45L)).thenReturn(null);
            assertFalse(Agenda.hasLiveReminderOwner(r));
            when(patients.findById(45L)).thenThrow(new IllegalStateException("offline"));
            assertFalse(Agenda.hasLiveReminderOwner(r));
        }
        assertFalse(Agenda.hasLiveReminderOwner(null));
        assertFalse(Agenda.hasLiveReminderOwner(reminder(null)));
        assertFalse(Agenda.hasLiveReminderOwner(reminder(0L)));
        assertFalse(Agenda.hasLiveReminderOwner(reminder(-1L)));
    }

    @Test
    public void orphanWithPendingEventMustNotBePublishedOrDeleteItsSqliteRow() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        EventReminder r = reminder(880119L);
        PatientDao patients = mock(PatientDao.class);
        EventReminderDao reminders = mock(EventReminderDao.class);
        EventInstanceDao events = mock(EventInstanceDao.class);
        when(reminders.findById(r.getId())).thenReturn(r);
        when(events.existsPending(r.getEventType(), r.getDateTime(), r.getPatient()))
                .thenReturn(true);
        // The patient was removed, but a dangling legacy reminder and intake survived.
        when(patients.findById(880119L)).thenReturn(null);
        PendingIntent intent = PendingIntent.getBroadcast(context, 0,
                Agenda.reminderBroadcastIntent(context, r),
                PendingIntentFlags.immutable(PendingIntent.FLAG_UPDATE_CURRENT));
        assertNotNull(intent);
        try (MockedStatic<DB> db = mockStatic(DB.class)) {
            db.when(DB::eventReminders).thenReturn(reminders);
            db.when(DB::eventInstances).thenReturn(events);
            db.when(DB::patients).thenReturn(patients);
            Agenda.instance().onReceiveAlarm(context, r.getId());
            assertNull("Missing patient must retire the stale OS alarm token",
                    PendingIntent.getBroadcast(context, 0,
                            Agenda.reminderBroadcastIntent(context, r),
                            PendingIntentFlags.immutable(PendingIntent.FLAG_NO_CREATE)));
            verify(reminders, never()).delete(any(EventReminder.class));
            verify(reminders).findById(r.getId());
        } finally {
            intent.cancel();
        }
    }
}
