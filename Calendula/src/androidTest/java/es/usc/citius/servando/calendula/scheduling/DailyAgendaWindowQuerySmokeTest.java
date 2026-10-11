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
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Synthetic SQLite rows only: display filtering must never delete archive data. */
@RunWith(AndroidJUnit4.class)
public class DailyAgendaWindowQuerySmokeTest {
    @Test
    public void boundedHomeAgendaShowsOnlyThreeDaysWithoutDeletingHistory() {
        assertTrue(DB.initialized);
        DateTime todayStart = DateTime.now().withTimeAtStartOfDay();
        DateTime from = todayStart.minusDays(1);
        DateTime until = todayStart.plusDays(2);
        Long syntheticRef = -System.nanoTime();

        EventInstance older = new EventInstance(
                from.minusDays(30), EventType.MEDICATION_INTAKE);
        older.setCompleted(true);
        EventInstance yesterday = new EventInstance(
                from.plusHours(12), EventType.MEDICATION_INTAKE);
        EventInstance today = new EventInstance(
                todayStart.plusHours(12), EventType.MEDICATION_INTAKE);
        EventInstance tomorrow = new EventInstance(
                todayStart.plusDays(1).plusHours(12), EventType.MEDICATION_INTAKE);
        EventInstance later = new EventInstance(
                until.plusDays(30), EventType.MEDICATION_INTAKE);
        later.setCancelled(true);
        EventInstance[] fixtures = {older, yesterday, today, tomorrow, later};
        try {
            for (EventInstance event : fixtures) {
                event.setRef(syntheticRef);
                DB.eventInstances().save(event);
                assertNotNull(event.getId());
            }
            List<EventInstance> visible = DB.eventInstances().findByTypeInWindow(
                    EventType.MEDICATION_INTAKE, from, until);
            int matched = 0;
            for (EventInstance event : visible) {
                if (syntheticRef.equals(event.getRef())) {
                    matched++;
                    assertTrue("Visible synthetic event must be within three days",
                            !event.getTime().isBefore(from) && event.getTime().isBefore(until));
                }
            }
            assertEquals("Only yesterday, today and tomorrow reach the agenda", 3, matched);
            assertNotNull("Older confirmed history remains durable in SQLite",
                    DB.eventInstances().findById(older.getId()));
            assertNotNull("Far-future cancelled history remains durable in SQLite",
                    DB.eventInstances().findById(later.getId()));
            assertTrue(DB.eventInstances().findById(older.getId()).completed());
            assertTrue(DB.eventInstances().findById(later.getId()).cancelled());
            assertTrue("Empty or inverted windows return no rows",
                    DB.eventInstances().findByTypeInWindow(
                            EventType.MEDICATION_INTAKE, until, from).isEmpty());
        } finally {
            for (EventInstance event : fixtures) {
                if (event.getId() != null && DB.eventInstances().findById(event.getId()) != null) {
                    DB.eventInstances().remove(event);
                }
            }
        }
    }
}
