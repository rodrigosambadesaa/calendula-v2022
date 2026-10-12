/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.persistence;

import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Real SQLite failure injection; synthetic references and past timestamps only. */
@RunWith(AndroidJUnit4.class)
public class ScheduleReconciliationSmokeTest {
    @Test
    public void failedObsoleteDeleteRollsBackEarlierDoseUpdateAndNewOccurrences() {
        assertTrue(DB.initialized);
        Schedule schedule = new Schedule();
        schedule.setId(-System.nanoTime());
        DateTime from = DateTime.now().minusDays(6);
        DateTime existingTime = from.plusDays(1).withMillisOfSecond(0);
        DateTime obsoleteTime = from.plusDays(2).withMillisOfSecond(0);
        DateTime newTime = from.plusDays(3).withMillisOfSecond(0);
        EventInstance existing = event(schedule.getId(), existingTime, 1.0d);
        EventInstance obsolete = event(schedule.getId(), obsoleteTime, 2.0d);
        EventInstance revised = event(schedule.getId(), existingTime, 5.0d);
        EventInstance added = event(schedule.getId(), newTime, 3.0d);
        SQLiteDatabase sqlite = DB.helper().getWritableDatabase();
        final String trigger = "ci_test_recurrence_delete_abort";
        sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
        try {
            DB.eventInstances().save(existing);
            DB.eventInstances().save(obsolete);
            assertNotNull(existing.getId());
            assertNotNull(obsolete.getId());
            sqlite.execSQL("CREATE TRIGGER " + trigger
                    + " BEFORE DELETE ON EventInstances WHEN OLD._id = " + obsolete.getId()
                    + " BEGIN SELECT RAISE(ABORT, 'synthetic recurrence failure'); END;");
            try {
                ScheduleUtils.instance().reconcileScheduleOccurrences(
                        InstrumentationRegistry.getInstrumentation().getTargetContext(),
                        schedule, from, Arrays.asList(revised, added));
                fail("Failed pruning must roll back earlier recurrence changes");
            } catch (RuntimeException expected) {
                // The previous event update must not survive this later failure.
            }
            assertEquals("Original pending dose must survive rollback",
                    1.0d, DB.eventInstances().findById(existing.getId())
                            .getDoubleParam(EventInstance.PARAM_DOSE), 0.0001d);
            assertNotNull("Obsolete pending row must survive rollback",
                    DB.eventInstances().findById(obsolete.getId()));
            assertFalse("New time must not have been inserted before SQL COMMIT",
                    DB.eventInstances().exists(EventType.MEDICATION_INTAKE,
                            newTime, schedule.getId()));

            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            ScheduleUtils.instance().reconcileScheduleOccurrences(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(),
                    schedule, from, Arrays.asList(revised, added));
            assertEquals("Retry updates original pending dose",
                    5.0d, DB.eventInstances().findById(existing.getId())
                            .getDoubleParam(EventInstance.PARAM_DOSE), 0.0001d);
            assertNull("Retry removes the obsolete pending row",
                    DB.eventInstances().findById(obsolete.getId()));
            assertTrue("Retry adds the new occurrence once",
                    DB.eventInstances().exists(EventType.MEDICATION_INTAKE,
                            newTime, schedule.getId()));
        } finally {
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            for (EventInstance row : DB.eventInstances().findByTypeAndTime(
                    EventType.MEDICATION_INTAKE, newTime)) {
                if (schedule.getId().equals(row.getRef())) {
                    DB.eventInstances().remove(row);
                }
            }
            for (EventInstance row : new EventInstance[]{existing, obsolete}) {
                if (row.getId() != null && DB.eventInstances().findById(row.getId()) != null) {
                    DB.eventInstances().remove(row);
                }
            }
        }
    }

    private static EventInstance event(Long scheduleId, DateTime time, double dose) {
        EventInstance event = new EventInstance(time, EventType.MEDICATION_INTAKE);
        event.setRef(scheduleId);
        event.addParam(EventInstance.PARAM_DOSE, dose);
        return event;
    }
}
