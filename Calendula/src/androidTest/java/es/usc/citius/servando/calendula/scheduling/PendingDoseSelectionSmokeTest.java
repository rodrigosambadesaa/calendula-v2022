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

import java.util.List;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Real device SQLite regression: a notification's pending dose list must
 * agree with the delivery predicate and never contain previously handled
 * doses or events belonging to another patient. Synthetic records only.
 */
@RunWith(AndroidJUnit4.class)
public class PendingDoseSelectionSmokeTest {

    @Test
    public void pendingMedicationItemsExcludeCompletedCancelledAndOtherPatients() {
        assertTrue(DB.initialized);
        DateTime time = DateTime.now().plusHours(14).withMillisOfSecond(0);
        Patient a = new Patient();
        a.setCode("ci-pending-dose-a-" + System.nanoTime());
        a.setName("Synthetic pending dose A");
        Patient b = new Patient();
        b.setCode("ci-pending-dose-b-" + System.nanoTime());
        b.setName("Synthetic pending dose B");

        EventInstance activeA = new EventInstance(time, EventType.MEDICATION_INTAKE);
        activeA.setPatient(a);
        EventInstance completedA = new EventInstance(time, EventType.MEDICATION_INTAKE);
        completedA.setPatient(a);
        completedA.setCompleted(true);
        EventInstance cancelledA = new EventInstance(time, EventType.MEDICATION_INTAKE);
        cancelledA.setPatient(a);
        cancelledA.setCancelled(true);
        EventInstance activeB = new EventInstance(time, EventType.MEDICATION_INTAKE);
        activeB.setPatient(b);

        EventInstance[] fixtures = {activeA, completedA, cancelledA, activeB};
        try {
            DB.patients().save(a);
            DB.patients().save(b);
            for (EventInstance event : fixtures) {
                DB.eventInstances().save(event);
                assertNotNull(event.getId());
            }

            List<EventInstance> dueForA = DB.eventInstances().findPending(
                    EventType.MEDICATION_INTAKE, time, a);
            assertEquals("Only A's active dose may appear in A's notification",
                    1, dueForA.size());
            assertEquals(activeA.getId(), dueForA.get(0).getId());
            assertTrue(DB.eventInstances().existsPending(
                    EventType.MEDICATION_INTAKE, time, a));

            List<EventInstance> dueForB = DB.eventInstances().findPending(
                    EventType.MEDICATION_INTAKE, time, b);
            assertEquals("A's events must never appear in B's notification",
                    1, dueForB.size());
            assertEquals(activeB.getId(), dueForB.get(0).getId());

            activeA.setCompleted(true);
            DB.eventInstances().save(activeA);
            assertFalse("No remaining active dose for A should authorize delivery",
                    DB.eventInstances().existsPending(
                            EventType.MEDICATION_INTAKE, time, a));
            assertTrue("A completed/cancelled patient's dose list must be empty",
                    DB.eventInstances().findPending(
                            EventType.MEDICATION_INTAKE, time, a).isEmpty());
            assertEquals("B remains unaffected after A's completion",
                    activeB.getId(), DB.eventInstances().findPending(
                            EventType.MEDICATION_INTAKE, time, b).get(0).getId());
        } finally {
            for (EventInstance event : fixtures) {
                if (event.getId() != null
                        && DB.eventInstances().findById(event.getId()) != null) {
                    DB.eventInstances().remove(event);
                }
            }
            if (a.getId() != null) {
                DB.patients().remove(a);
            }
            if (b.getId() != null) {
                DB.patients().remove(b);
            }
        }
    }

    @Test
    public void nullPatientSelectionCannotIncludeNamedPatientRecords() {
        assertTrue(DB.initialized);
        DateTime time = DateTime.now().plusHours(15).withMillisOfSecond(0);
        Patient named = new Patient();
        named.setCode("ci-pending-dose-named-" + System.nanoTime());
        named.setName("Synthetic named patient");
        EventInstance orphan = new EventInstance(time, EventType.MEDICATION_INTAKE);
        EventInstance namedEvent = new EventInstance(time, EventType.MEDICATION_INTAKE);
        namedEvent.setPatient(named);
        try {
            DB.patients().save(named);
            DB.eventInstances().save(orphan);
            DB.eventInstances().save(namedEvent);
            List<EventInstance> selected = DB.eventInstances().findPending(
                    EventType.MEDICATION_INTAKE, time, null);
            assertEquals("Only a null-patient row can match the null scope",
                    1, selected.size());
            assertEquals(orphan.getId(), selected.get(0).getId());
            assertTrue(DB.eventInstances().existsPending(
                    EventType.MEDICATION_INTAKE, time, null));
        } finally {
            for (EventInstance row : new EventInstance[]{orphan, namedEvent}) {
                if (row.getId() != null && DB.eventInstances().findById(row.getId()) != null) {
                    DB.eventInstances().remove(row);
                }
            }
            if (named.getId() != null) {
                DB.patients().remove(named);
            }
        }
    }
}
