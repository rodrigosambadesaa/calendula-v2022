/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
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

/** Isolated synthetic records, never actual patient/prescription data. */
@RunWith(AndroidJUnit4.class)
public class NullablePatientDaoSmokeTest {

    @Test
    public void nullablePatientForeignKeysCanBeFoundAndRemovedOnSQLite() {
        assertTrue(DB.initialized);
        DateTime future = DateTime.now().plusHours(7).withMillisOfSecond(0);

        EventInstance event = new EventInstance(future, EventType.MEDICATION_INTAKE);
        EventReminder reminder = new EventReminder(future, EventType.MEDICATION_INTAKE);
        reminder.setNextTime(future);

        try {
            DB.eventInstances().save(event);
            DB.eventReminders().save(reminder);
            assertNotNull(event.getId());
            assertNotNull(reminder.getId());

            assertTrue("Event with null patient should be queryable",
                    DB.eventInstances().exists(EventType.MEDICATION_INTAKE,
                            future, null, false));
            assertTrue("Unassigned active events must remain queryable",
                    DB.eventInstances().existsPending(EventType.MEDICATION_INTAKE,
                            future, null));
            assertEquals(1, DB.eventInstances().find(
                    EventType.MEDICATION_INTAKE, future, null).size());
            assertTrue("Reminder with null patient should be queryable",
                    DB.eventReminders().exists(EventType.MEDICATION_INTAKE, future, null));
            assertEquals(reminder.getId(), DB.eventReminders()
                    .findBy(EventType.MEDICATION_INTAKE, future, null).getId());

            assertEquals("Explicit null-patient row deletion must be scoped",
                    1, DB.eventReminders().removeBy(
                            null, EventType.MEDICATION_INTAKE, future));
            event.setCancelled(true);
            DB.eventInstances().save(event);
            assertFalse("A cancelled unassigned intake must not be deliverable",
                    DB.eventInstances().existsPending(EventType.MEDICATION_INTAKE,
                            future, null));
        } finally {
            if (reminder.getId() != null && DB.eventReminders().findById(
                    reminder.getId()) != null) {
                DB.eventReminders().remove(reminder);
            }
            if (event.getId() != null && DB.eventInstances().findById(
                    event.getId()) != null) {
                DB.eventInstances().remove(event);
            }
        }
    }
    @Test
    public void nullPatientUpdatesCannotChangeAnotherPatientsMedicationEvents() {
        DateTime future = DateTime.now().plusHours(8).withMillisOfSecond(0);
        Patient named = new Patient();
        named.setCode("ci-null-fk-" + System.nanoTime());
        named.setName("Synthetic second patient");

        EventInstance unassigned = new EventInstance(future, EventType.MEDICATION_INTAKE);
        EventInstance assigned = new EventInstance(future, EventType.MEDICATION_INTAKE);
        assigned.setPatient(named);

        EventReminder noPatient = new EventReminder(future, EventType.MEDICATION_INTAKE);
        noPatient.setNextTime(future);
        EventReminder namedReminder = new EventReminder(future, EventType.MEDICATION_INTAKE);
        namedReminder.setNextTime(future);
        namedReminder.setPatient(named);

        try {
            DB.patients().save(named);
            DB.eventInstances().save(unassigned);
            DB.eventInstances().save(assigned);
            DB.eventReminders().save(noPatient);
            DB.eventReminders().save(namedReminder);

            boolean rejected = false;
            try {
                DB.eventInstances().confirm(
                        EventType.MEDICATION_INTAKE, future, null, DateTime.now());
            } catch (IllegalStateException expected) {
                rejected = true;
            }
            assertTrue("Status-only DAO confirmation must reject medication intakes", rejected);
            assertFalse("Unassigned intake cannot bypass medicine inventory updates",
                    DB.eventInstances().findById(unassigned.getId()).completed());
            assertFalse("Another patient's intake cannot be changed by a rejected request",
                    DB.eventInstances().findById(assigned.getId()).completed());
            boolean assignedRejected = false;
            try {
                DB.eventInstances().confirm(
                        EventType.MEDICATION_INTAKE, future, named, DateTime.now());
            } catch (IllegalStateException expected) {
                assignedRejected = true;
            }
            assertTrue("A named-patient intake must also reject DAO-only confirmation",
                    assignedRejected);
            assertFalse("No named-patient event may bypass its medicine stock ledger",
                    DB.eventInstances().findById(assigned.getId()).completed());

            assertEquals(1, DB.eventReminders().removeBy(
                    null, EventType.MEDICATION_INTAKE, future));
            assertNotNull("Other person's reminder must remain persisted",
                    DB.eventReminders().findById(namedReminder.getId()));
            assertEquals("Second null-patient delete must leave named rows alone", 0,
                    DB.eventReminders().removeBy(
                            null, EventType.MEDICATION_INTAKE, future));
        } finally {
            for (EventInstance event : new EventInstance[]{unassigned, assigned}) {
                if (event.getId() != null && DB.eventInstances().findById(event.getId()) != null) {
                    DB.eventInstances().remove(event);
                }
            }
            for (EventReminder reminder : new EventReminder[]{noPatient, namedReminder}) {
                if (reminder.getId() != null && DB.eventReminders().findById(reminder.getId()) != null) {
                    DB.eventReminders().remove(reminder);
                }
            }
            if (named.getId() != null) {
                DB.patients().remove(named);
            }
        }
    }



    @Test
    public void nonMedicationBulkConfirmStillScopesNullablePatientCorrectly() {
        assertTrue(DB.initialized);
        DateTime future = DateTime.now().plusHours(10).withMillisOfSecond(0);
        Patient named = new Patient();
        named.setCode("ci-nonmedicine-bulk-" + System.nanoTime());
        named.setName("Synthetic pharmacy reminder patient");
        EventInstance unassigned = new EventInstance(future, EventType.PHARMACY_REMINDER);
        EventInstance assigned = new EventInstance(future, EventType.PHARMACY_REMINDER);
        assigned.setPatient(named);
        try {
            DB.patients().save(named);
            DB.eventInstances().save(unassigned);
            DB.eventInstances().save(assigned);
            assertEquals("Pharmacy-only bulk confirmation is independent of medicine stock",
                    1, DB.eventInstances().confirm(
                            EventType.PHARMACY_REMINDER, future, null, DateTime.now()));
            assertTrue(DB.eventInstances().findById(unassigned.getId()).completed());
            assertFalse(DB.eventInstances().findById(assigned.getId()).completed());
            assertEquals("Repeated non-medication bulk confirm stays idempotent",
                    0, DB.eventInstances().confirm(
                            EventType.PHARMACY_REMINDER, future, null, DateTime.now()));
        } finally {
            for (EventInstance item : new EventInstance[]{unassigned, assigned}) {
                if (item.getId() != null && DB.eventInstances().findById(item.getId()) != null) {
                    DB.eventInstances().remove(item);
                }
            }
            if (named.getId() != null) {
                DB.patients().remove(named);
            }
        }
    }
}
