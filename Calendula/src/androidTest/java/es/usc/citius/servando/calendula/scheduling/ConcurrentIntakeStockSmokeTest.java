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

/**
 * Real SQLite with two simultaneous callbacks for one synthetic dose.
 * Never uses an actual patient's record or stock.
 */
@RunWith(AndroidJUnit4.class)
public class ConcurrentIntakeStockSmokeTest {
    @Test
    public void twoConcurrentConfirmationsDebitTrackedStockExactlyOnce() throws Exception {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Patient patient = new Patient();
        patient.setCode("ci-concurrent-stock-" + System.nanoTime());
        patient.setName("Synthetic double-tap patient");
        Medicine medicine = new Medicine("Synthetic double-tap medicine", Presentation.PILLS);
        Schedule schedule = new Schedule(medicine);
        DateTime scheduledAt = DateTime.now().plusHours(13).withMillisOfSecond(0);
        EventInstance event = new EventInstance(scheduledAt, EventType.MEDICATION_INTAKE);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            assertEquals(1, DB.patients().create(patient));
            medicine.setPatient(patient);
            medicine.setStock(10.0f);
            assertEquals(1, DB.medicines().create(medicine));
            schedule.setPatient(patient);
            assertEquals(1, DB.schedules().create(schedule));
            event.setPatient(patient);
            event.setRef(schedule.getId());
            event.addParam(EventInstance.PARAM_DOSE, 2.0d);
            assertEquals(1, DB.eventInstances().create(event));

            // Both caller snapshots originate from the same SQLite state,
            // as with two simultaneous clicks on a stale confirmation view.
            EventInstance first = DB.eventInstances().findById(event.getId());
            EventInstance second = DB.eventInstances().findById(event.getId());
            assertNotNull(first);
            assertNotNull(second);
            assertFalse(first.completed());
            assertFalse(second.completed());
            CountDownLatch gate = new CountDownLatch(1);
            Future<Boolean> one = executor.submit(() -> {
                gate.await();
                return ScheduleUtils.instance().setIntakeCompleted(context, first, true);
            });
            Future<Boolean> two = executor.submit(() -> {
                gate.await();
                return ScheduleUtils.instance().setIntakeCompleted(context, second, true);
            });
            gate.countDown();
            boolean firstResult = one.get(90, TimeUnit.SECONDS);
            boolean secondResult = two.get(90, TimeUnit.SECONDS);

            assertTrue("Exactly one callback may transition from pending to taken",
                    firstResult ^ secondResult);
            assertTrue(DB.eventInstances().findById(event.getId()).completed());
            assertEquals("Stock must be debited once, never twice",
                    8.0f, DB.medicines().findById(medicine.getId()).getStock(), 0.001f);
        } finally {
            executor.shutdownNow();
            EventReminder reminder = patient.getId() == null ? null
                    : DB.eventReminders().findBy(
                            EventType.MEDICATION_INTAKE, scheduledAt, patient);
            if (reminder != null) {
                Agenda.instance().cancelAlarm(context, reminder);
                DB.eventReminders().remove(reminder);
            }
            if (event.getId() != null && DB.eventInstances().findById(event.getId()) != null) {
                DB.eventInstances().remove(event);
            }
            if (schedule.getId() != null && DB.schedules().findById(schedule.getId()) != null) {
                DB.schedules().remove(schedule);
            }
            if (medicine.getId() != null && DB.medicines().findById(medicine.getId()) != null) {
                DB.medicines().remove(medicine);
            }
            if (patient.getId() != null && DB.patients().findById(patient.getId()) != null) {
                DB.patients().remove(patient);
            }
        }
    }
}
