/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.database.EventInstanceDao;
import es.usc.citius.servando.calendula.database.EventReminderDao;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** Never re-create a reminder that the receiver or patient already removed. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class NoReminderResurrectionTest {

    @Test
    public void missingOrCancelledPendingRecordCannotBeRearmed() {
        DateTime time = DateTime.now().plusHours(4);
        EventReminder reminder = new EventReminder(time, EventType.MEDICATION_INTAKE);
        reminder.setId(9000003401L);
        reminder.setNextTime(time);
        EventReminderDao reminders = mock(EventReminderDao.class);
        EventInstanceDao instances = mock(EventInstanceDao.class);

        try (MockedStatic<DB> db = mockStatic(DB.class)) {
            db.when(DB::eventReminders).thenReturn(reminders);
            db.when(DB::eventInstances).thenReturn(instances);
            assertFalse("Missing persisted rows must never be resurrected",
                    Agenda.instance().isCurrentPendingReminder(reminder));

            when(reminders.findById(reminder.getId())).thenReturn(reminder);
            assertFalse("Cancelled/completed event must not be rearmed",
                    Agenda.instance().isCurrentPendingReminder(reminder));

            when(instances.existsPending(reminder.getEventType(),
                    reminder.getDateTime(), null)).thenReturn(true);
            assertTrue("A persisted, pending reminder at its original time may repeat",
                    Agenda.instance().isCurrentPendingReminder(reminder));

            EventReminder delayed = new EventReminder(time, EventType.MEDICATION_INTAKE);
            delayed.setId(reminder.getId());
            delayed.setNextTime(time.plusMinutes(10));
            when(reminders.findById(reminder.getId())).thenReturn(delayed);
            assertFalse("Stale alarm cannot overwrite a patient's changed delay",
                    Agenda.instance().isCurrentPendingReminder(reminder));
        }
    }
}
