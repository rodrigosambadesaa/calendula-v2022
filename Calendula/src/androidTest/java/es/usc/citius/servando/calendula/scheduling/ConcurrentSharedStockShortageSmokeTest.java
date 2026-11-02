/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.persistence.ScheduleUtils;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;

import static org.junit.Assert.*;

/** Two distinct doses competing for a single, insufficient synthetic stock. */
@RunWith(AndroidJUnit4.class)
public class ConcurrentSharedStockShortageSmokeTest {

    @Test
    public void concurrentDifferentDosesCannotOverdrawSharedTrackedInventory()
            throws Exception {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Patient owner = new Patient();
        owner.setCode("ci-overdraw-" + System.nanoTime());
        owner.setName("Synthetic concurrent stock patient");
        Medicine medicine = new Medicine("Synthetic shared inventory", Presentation.PILLS);
        Schedule schedule = new Schedule(medicine);
        DateTime firstAt = DateTime.now().plusHours(13).withMillisOfSecond(0);
        DateTime secondAt = firstAt.plusMinutes(5);
        EventInstance first = new EventInstance(firstAt, EventType.MEDICATION_INTAKE);
        EventInstance second = new EventInstance(secondAt, EventType.MEDICATION_INTAKE);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            assertEquals(1, DB.patients().create(owner));
            medicine.setPatient(owner);
            medicine.setStock(3.0f);
            assertEquals(1, DB.medicines().create(medicine));
            schedule.setPatient(owner);
            assertEquals(1, DB.schedules().create(schedule));
            for (EventInstance event : new EventInstance[]{first, second}) {
                event.setPatient(owner);
                event.setRef(schedule.getId());
                event.addParam(EventInstance.PARAM_DOSE, 2.0d);
                assertEquals(1, DB.eventInstances().create(event));
            }

            EventInstance firstSnapshot = DB.eventInstances().findById(first.getId());
            EventInstance secondSnapshot = DB.eventInstances().findById(second.getId());
            assertNotNull(firstSnapshot);
            assertNotNull(secondSnapshot);
            assertFalse(firstSnapshot.completed());
            assertFalse(secondSnapshot.completed());

            CountDownLatch start = new CountDownLatch(1);
            Future<Boolean> firstResult = workers.submit(() -> {
                start.await();
                try {
                    return ScheduleUtils.instance().setIntakeCompleted(context, firstSnapshot, true);
                } catch (IllegalStateException insufficientStock) {
                    return false;
                }
            });
            Future<Boolean> secondResult = workers.submit(() -> {
                start.await();
                try {
                    return ScheduleUtils.instance().setIntakeCompleted(context, secondSnapshot, true);
                } catch (IllegalStateException insufficientStock) {
                    return false;
                }
            });
            start.countDown();
            boolean one = firstResult.get(90, TimeUnit.SECONDS);
            boolean two = secondResult.get(90, TimeUnit.SECONDS);

            assertTrue("Only one of two distinct 2-unit doses can use 3 units", one ^ two);
            assertEquals("No lost updates or overdraft are allowed", 1.0f,
                    DB.medicines().findById(medicine.getId()).getStock(), 0.001f);
            assertEquals("Only the successfully committed intake is recorded",
                    1, (DB.eventInstances().findById(first.getId()).completed() ? 1 : 0)
                            + (DB.eventInstances().findById(second.getId()).completed() ? 1 : 0));
        } finally {
            workers.shutdownNow();
            assertTrue("Worker threads must stop before test data cleanup",
                    workers.awaitTermination(15, TimeUnit.SECONDS));
            for (DateTime at : new DateTime[]{firstAt, secondAt}) {
                EventReminder reminder = owner.getId() == null ? null
                        : DB.eventReminders().findBy(EventType.MEDICATION_INTAKE, at, owner);
                if (reminder != null) {
                    Agenda.instance().cancelAlarm(context, reminder);
                    DB.eventReminders().remove(reminder);
                }
            }
            for (EventInstance event : new EventInstance[]{first, second}) {
                if (event.getId() != null
                        && DB.eventInstances().findById(event.getId()) != null) {
                    DB.eventInstances().remove(event);
                }
            }
            if (schedule.getId() != null && DB.schedules().findById(schedule.getId()) != null) {
                DB.schedules().remove(schedule);
            }
            if (medicine.getId() != null && DB.medicines().findById(medicine.getId()) != null) {
                DB.medicines().remove(medicine);
            }
            if (owner.getId() != null && DB.patients().findById(owner.getId()) != null) {
                DB.patients().remove(owner);
            }
        }
    }
}
