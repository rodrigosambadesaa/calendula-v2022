/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Genuine SQLite validation of the complete reminder identity.
 * Only disposable synthetic rows; no medication, stock or OS alarms.
 */
@RunWith(AndroidJUnit4.class)
public class CurrentReminderIdentitySmokeTest {

    @Test
    public void unchangedAlarmTimeCannotAuthorizeReassignedPatientOrIntakeDate()
            throws Exception {
        assertTrue(DB.initialized);
        DateTime when = DateTime.now().plusHours(7).withMillisOfSecond(0);
        DateTime alternate = when.plusMinutes(30);
        Patient a = new Patient();
        a.setCode("ci-stale-reminder-a-" + System.nanoTime());
        a.setName("Synthetic patient A");
        Patient b = new Patient();
        b.setCode("ci-stale-reminder-b-" + System.nanoTime());
        b.setName("Synthetic patient B");
        EventInstance doseA = new EventInstance(when, EventType.MEDICATION_INTAKE);
        EventInstance doseB = new EventInstance(when, EventType.MEDICATION_INTAKE);
        EventInstance laterB = new EventInstance(alternate, EventType.MEDICATION_INTAKE);
        EventReminder cached = new EventReminder(when, EventType.MEDICATION_INTAKE);
        cached.setNextTime(when);
        try {
            assertEquals(1, DB.patients().create(a));
            assertEquals(1, DB.patients().create(b));
            doseA.setPatient(a);
            doseB.setPatient(b);
            laterB.setPatient(b);
            assertEquals(1, DB.eventInstances().create(doseA));
            assertEquals(1, DB.eventInstances().create(doseB));
            assertEquals(1, DB.eventInstances().create(laterB));
            cached.setPatient(a);
            assertEquals(1, DB.eventReminders().create(cached));
            assertNotNull(cached.getId());
            assertTrue("Original pending reminder must remain deliverable",
                    Agenda.instance().isCurrentPendingReminder(cached));

            // Reassign the SAME reminder ID with the SAME NextTime to B.
            // Both patients have a pending event at the original time:
            // checking only ID, NextTime and a generic pending dose was
            // previously insufficient to reject A's cached reminder.
            EventReminder reassigned = DB.eventReminders().findById(cached.getId());
            reassigned.setPatient(b);
            assertEquals(1, DB.eventReminders().update(reassigned));
            assertFalse("Cached patient A reminder must not deliver B's medication",
                    Agenda.instance().isCurrentPendingReminder(cached));
            EventReminder current = DB.eventReminders().findById(cached.getId());
            assertTrue("The new version is still a valid pending B reminder",
                    Agenda.instance().isCurrentPendingReminder(current));

            // Move the persisted appointment date, but leave NextTime
            // unchanged. A cached notification for the old date is stale.
            EventReminder previousDate = current;
            EventReminder moved = DB.eventReminders().findById(cached.getId());
            moved.setDateTime(alternate);
            assertEquals(1, DB.eventReminders().update(moved));
            assertFalse("A different dose time invalidates the previous snapshot",
                    Agenda.instance().isCurrentPendingReminder(previousDate));
            assertTrue(Agenda.instance().isCurrentPendingReminder(
                    DB.eventReminders().findById(cached.getId())));
        } finally {
            if (cached.getId() != null
                    && DB.eventReminders().findById(cached.getId()) != null) {
                DB.eventReminders().remove(cached);
            }
            for (EventInstance event : new EventInstance[]{doseA, doseB, laterB}) {
                if (event.getId() != null
                        && DB.eventInstances().findById(event.getId()) != null) {
                    DB.eventInstances().remove(event);
                }
            }
            if (a.getId() != null && DB.patients().findById(a.getId()) != null) {
                DB.patients().remove(a);
            }
            if (b.getId() != null && DB.patients().findById(b.getId()) != null) {
                DB.patients().remove(b);
            }
        }
    }
}
