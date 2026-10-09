/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.ScheduleUtils;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Medication confirmation is never allowed to reclassify cancelled doses or
 * complete events for another patient. Uses only synthetic SQLite fixtures.
 */
@RunWith(AndroidJUnit4.class)
public class CheckAllActiveIntakesSmokeTest {

    @Test
    public void checkAllPreservesCancelledAndAlreadyCompletedDosesAndOtherPatient() {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DateTime time = DateTime.now().plusHours(18).withMillisOfSecond(0);
        Patient a = new Patient();
        a.setCode("ci-checkall-a-" + System.nanoTime());
        a.setName("Synthetic cancelled intake A");
        Patient b = new Patient();
        b.setCode("ci-checkall-b-" + System.nanoTime());
        b.setName("Synthetic pending intake B");
        EventInstance cancelledA = new EventInstance(time, EventType.MEDICATION_INTAKE);
        cancelledA.setPatient(a);
        cancelledA.setCancelled(true);
        EventInstance completedA = new EventInstance(time, EventType.MEDICATION_INTAKE);
        completedA.setPatient(a);
        completedA.setCompleted(true);
        EventInstance pendingB = new EventInstance(time, EventType.MEDICATION_INTAKE);
        pendingB.setPatient(b);
        EventInstance[] fixtures = {cancelledA, completedA, pendingB};
        try {
            DB.patients().save(a);
            DB.patients().save(b);
            for (EventInstance fixture : fixtures) {
                DB.eventInstances().save(fixture);
                assertNotNull(fixture.getId());
            }

            int changed = ScheduleUtils.instance().checkIntakeEvents(context, a, time);
            assertEquals("No active doses for A; do not claim medicines taken", 0, changed);

            EventInstance cancelledAfter = DB.eventInstances().findById(cancelledA.getId());
            assertNotNull(cancelledAfter);
            assertTrue("Cancelled intake must retain cancellation status", cancelledAfter.cancelled());
            assertFalse("Cancelled intake must not be rewritten as completed",
                    cancelledAfter.completed());
            EventInstance completedAfter = DB.eventInstances().findById(completedA.getId());
            assertNotNull(completedAfter);
            assertTrue(completedAfter.completed());
            assertFalse(completedAfter.cancelled());
            EventInstance otherAfter = DB.eventInstances().findById(pendingB.getId());
            assertNotNull(otherAfter);
            assertFalse("Other patient's pending intake cannot be completed", otherAfter.completed());
            assertFalse(otherAfter.cancelled());

            assertEquals("Null patient cannot trigger a bulk intake confirmation", 0,
                    ScheduleUtils.instance().checkIntakeEvents(context, null, time));
            assertEquals("Null timestamp cannot trigger a bulk intake confirmation", 0,
                    ScheduleUtils.instance().checkIntakeEvents(context, a, null));
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
}
