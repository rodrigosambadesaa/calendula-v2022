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
import es.usc.citius.servando.calendula.scheduling.model.EventType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** No real patient data: calendar alarm candidates are fetched by SQLite time. */
@RunWith(AndroidJUnit4.class)
public class ReminderCandidateWindowSmokeTest {

    @Test
    public void candidateQueryExcludesArchiveAndFarFutureWithoutDeletingAnything() {
        assertTrue(DB.initialized);
        DateTime now = DateTime.now().withMillisOfSecond(0);
        DateTime from = now.minusMinutes(10);
        DateTime until = now.plusDays(1);
        Long syntheticRef = -System.nanoTime();

        EventInstance oldTaken = new EventInstance(
                now.minusYears(10), EventType.MEDICATION_INTAKE);
        oldTaken.setCompleted(true);
        EventInstance due = new EventInstance(
                now.plusMinutes(5), EventType.MEDICATION_INTAKE);
        EventInstance otherTypeDue = new EventInstance(
                now.plusMinutes(6), EventType.PHARMACY_REMINDER);
        EventInstance farFutureCancelled = new EventInstance(
                now.plusYears(10), EventType.MEDICATION_INTAKE);
        farFutureCancelled.setCancelled(true);
        EventInstance[] events = {oldTaken, due, otherTypeDue, farFutureCancelled};
        try {
            for (EventInstance event : events) {
                event.setRef(syntheticRef);
                DB.eventInstances().save(event);
                assertNotNull(event.getId());
            }

            int selected = 0;
            for (EventInstance event : DB.eventInstances().findInWindow(from, until)) {
                if (!syntheticRef.equals(event.getRef())) continue;
                selected++;
                assertTrue("Only events in the actual reminder time window are returned",
                        !event.getTime().isBefore(from)
                                && event.getTime().isBefore(until));
            }
            assertEquals("Both in-window event types remain available to their receivers",
                    2, selected);
            assertNotNull("Historical completed dose stays in SQLite",
                    DB.eventInstances().findById(oldTaken.getId()));
            assertNotNull("Far-future cancelled dose stays in SQLite",
                    DB.eventInstances().findById(farFutureCancelled.getId()));
            assertTrue("Reversed time ranges return an empty result",
                    DB.eventInstances().findInWindow(until, from).isEmpty());
        } finally {
            for (EventInstance event : events) {
                if (event.getId() != null && DB.eventInstances().findById(event.getId()) != null) {
                    DB.eventInstances().remove(event);
                }
            }
        }
    }
}
