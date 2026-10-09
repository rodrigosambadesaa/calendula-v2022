/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Real SQLite, synthetic records only, no actual medication delivery. */
@RunWith(AndroidJUnit4.class)
public class NoReminderResurrectionSmokeTest {

    @Test
    public void removedOrRetimedPersistedReminderCannotBeRepeatedFromStaleAlarm() {
        assertTrue(DB.initialized);
        DateTime time = DateTime.now().plusHours(16).withMillisOfSecond(0);
        EventInstance event = new EventInstance(time, EventType.MEDICATION_INTAKE);
        EventReminder reminder = new EventReminder(time, EventType.MEDICATION_INTAKE);
        reminder.setNextTime(time);
        try {
            DB.eventInstances().save(event);
            DB.eventReminders().save(reminder);
            assertNotNull(event.getId());
            assertNotNull(reminder.getId());
            assertTrue(Agenda.instance().isCurrentPendingReminder(reminder));

            EventReminder persisted = DB.eventReminders().findById(reminder.getId());
            assertNotNull(persisted);
            persisted.setNextTime(time.plusMinutes(7));
            DB.eventReminders().save(persisted);
            assertFalse("Stale in-memory time must not override user delay",
                    Agenda.instance().isCurrentPendingReminder(reminder));

            DB.eventReminders().remove(persisted);
            assertFalse("Deleted reminder must never be reconstructed on alarm receive",
                    Agenda.instance().isCurrentPendingReminder(reminder));
            assertNotNull("The event row is not silently deleted by this check",
                    DB.eventInstances().findById(event.getId()));
        } finally {
            if (reminder.getId() != null
                    && DB.eventReminders().findById(reminder.getId()) != null) {
                DB.eventReminders().remove(DB.eventReminders().findById(reminder.getId()));
            }
            if (event.getId() != null
                    && DB.eventInstances().findById(event.getId()) != null) {
                DB.eventInstances().remove(event);
            }
        }
    }
}
