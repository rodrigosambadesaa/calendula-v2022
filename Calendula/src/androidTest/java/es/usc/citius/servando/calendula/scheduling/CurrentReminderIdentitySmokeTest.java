/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import android.database.sqlite.SQLiteDatabase;

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
        EventReminder alternativeDateSource =
                new EventReminder(alternate, EventType.MEDICATION_INTAKE);
        alternativeDateSource.setNextTime(when);
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
            alternativeDateSource.setPatient(b);
            assertEquals(1, DB.eventReminders().create(alternativeDateSource));
            assertNotNull(alternativeDateSource.getId());
            SQLiteDatabase sqlite = DB.helper().getWritableDatabase();

            // Reassign the SAME reminder ID with the SAME NextTime to B.
            // Both patients have a pending event at the original time:
            // checking only ID, NextTime and a generic pending dose was
            // previously insufficient to reject A's cached reminder.
            // Direct SQL simulates restoring/reassigning the reminder row,
            // without depending on ORM's legacy update-count semantics.
            sqlite.execSQL("UPDATE EventReminders SET Patient = ? WHERE _id = ?",
                    new Object[]{b.getId(), cached.getId()});
            EventReminder current = DB.eventReminders().findById(cached.getId());
            assertNotNull("Reassigned row must still exist", current);
            assertEquals("SQLite must actually own the row for B", b.getId(),
                    current.getPatient().getId());
            assertFalse("Cached patient A reminder must not deliver B's medication",
                    Agenda.instance().isCurrentPendingReminder(cached));
            assertTrue("The new version is still a valid pending B reminder",
                    Agenda.instance().isCurrentPendingReminder(current));

            // Copy the exact on-disk DateTime representation from a
            // disposable reminder. This is portable across Joda/ORM
            // persistence formats on Android 6 through Android 17.
            EventReminder previousDate = current;
            sqlite.execSQL("UPDATE EventReminders SET DateTime = "
                            + "(SELECT DateTime FROM EventReminders WHERE _id = ?) "
                            + "WHERE _id = ?",
                    new Object[]{alternativeDateSource.getId(), cached.getId()});
            EventReminder moved = DB.eventReminders().findById(cached.getId());
            assertNotNull(moved);
            assertEquals("SQLite must actually contain the new appointment date",
                    alternate, moved.getDateTime());
            assertFalse("A different dose time invalidates the previous snapshot",
                    Agenda.instance().isCurrentPendingReminder(previousDate));
            assertTrue(Agenda.instance().isCurrentPendingReminder(moved));
        } finally {
            if (alternativeDateSource.getId() != null
                    && DB.eventReminders().findById(alternativeDateSource.getId()) != null) {
                DB.eventReminders().remove(alternativeDateSource);
            }
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
