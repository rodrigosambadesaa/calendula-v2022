/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Real SQLite history-retention test of the daily medication listener.
 * Synthetic patients and events only; no treatment, stock or OS alarms.
 */
@RunWith(AndroidJUnit4.class)
public class IntakeHistoryRetentionSmokeTest {

    @Test
    public void existingDoseTodayMustNotSuppressMissingOtherPatientDose() {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        LocalDate today = LocalDate.now();
        DateTime firstTime = today.toDateTimeAtStartOfDay().plusHours(20).plusMinutes(37);
        DateTime secondTime = today.toDateTimeAtStartOfDay().plusHours(21).plusMinutes(42);
        Patient first = new Patient();
        first.setCode("ci-rebuild-first-" + System.nanoTime());
        first.setName("Synthetic existing-today patient");
        Patient second = new Patient();
        second.setCode("ci-rebuild-second-" + System.nanoTime());
        second.setName("Synthetic missing-today patient");
        es.usc.citius.servando.calendula.persistence.Medicine firstMedicine =
                new es.usc.citius.servando.calendula.persistence.Medicine(
                        "Synthetic first medicine",
                        es.usc.citius.servando.calendula.persistence.Presentation.PILLS);
        es.usc.citius.servando.calendula.persistence.Medicine secondMedicine =
                new es.usc.citius.servando.calendula.persistence.Medicine(
                        "Synthetic second medicine",
                        es.usc.citius.servando.calendula.persistence.Presentation.PILLS);
        es.usc.citius.servando.calendula.persistence.Schedule firstSchedule =
                new es.usc.citius.servando.calendula.persistence.Schedule(
                        new es.usc.citius.servando.calendula.scheduling.model.recur.RecurringEvent.Builder()
                                .repeatDaily().from(today)
                                .atFixedTime(new org.joda.time.LocalTime(20, 37)).build());
        es.usc.citius.servando.calendula.persistence.Schedule secondSchedule =
                new es.usc.citius.servando.calendula.persistence.Schedule(
                        new es.usc.citius.servando.calendula.scheduling.model.recur.RecurringEvent.Builder()
                                .repeatDaily().from(today)
                                .atFixedTime(new org.joda.time.LocalTime(21, 42)).build());
        EventInstance completed = null;
        try {
            assertEquals(1, DB.patients().create(first));
            assertEquals(1, DB.patients().create(second));
            firstMedicine.setPatient(first);
            secondMedicine.setPatient(second);
            assertEquals(1, DB.medicines().create(firstMedicine));
            assertEquals(1, DB.medicines().create(secondMedicine));
            firstSchedule.setPatient(first);
            firstSchedule.setMedicine(firstMedicine);
            firstSchedule.setDosages(java.util.Arrays.asList(1.0d));
            secondSchedule.setPatient(second);
            secondSchedule.setMedicine(secondMedicine);
            secondSchedule.setDosages(java.util.Arrays.asList(0.5d));
            assertEquals(1, DB.schedules().create(firstSchedule));
            assertEquals(1, DB.schedules().create(secondSchedule));

            completed = new EventInstance(firstTime, EventType.MEDICATION_INTAKE);
            completed.setPatient(first);
            completed.setRef(firstSchedule.getId());
            completed.addParam(EventInstance.PARAM_DOSE, 1.0d);
            completed.setCompleted(true);
            completed.setCompletedAt(firstTime.plusMinutes(3));
            assertEquals(1, DB.eventInstances().create(completed));
            Long completedId = completed.getId();
            assertNotNull(completedId);
            assertFalse("Missing second patient dose is our precondition",
                    DB.eventInstances().exists(
                            EventType.MEDICATION_INTAKE, secondTime, secondSchedule.getId()));

            MedicationIntakeReceiver receiver = new MedicationIntakeReceiver();
            receiver.onBeforeUpdate(context, today);
            assertTrue("A preexisting dose must not hide another patient's missing dose",
                    DB.eventInstances().exists(
                            EventType.MEDICATION_INTAKE, secondTime, secondSchedule.getId()));
            EventInstance stillCompleted = DB.eventInstances().findById(completedId);
            assertNotNull(stillCompleted);
            assertTrue("Original taken decision must remain unchanged", stillCompleted.completed());
            assertEquals(firstTime.plusMinutes(3), stillCompleted.completedAt());

            receiver.onBeforeUpdate(context, today);
            assertEquals("Repeat regeneration must not create duplicate doses",
                    1, DB.eventInstances().find(EventType.MEDICATION_INTAKE, secondTime, second).size());
            assertNotNull(DB.eventInstances().findById(completedId));
        } finally {
            if (firstSchedule.getId() != null) {
                DB.eventInstances().removeByRef(firstSchedule.getId());
                if (DB.schedules().findById(firstSchedule.getId()) != null) {
                    DB.schedules().remove(firstSchedule);
                }
            }
            if (secondSchedule.getId() != null) {
                DB.eventInstances().removeByRef(secondSchedule.getId());
                if (DB.schedules().findById(secondSchedule.getId()) != null) {
                    DB.schedules().remove(secondSchedule);
                }
            }
            for (es.usc.citius.servando.calendula.persistence.Medicine medicine :
                    new es.usc.citius.servando.calendula.persistence.Medicine[]{
                            firstMedicine, secondMedicine}) {
                if (medicine.getId() != null && DB.medicines().findById(medicine.getId()) != null) {
                    DB.medicines().remove(medicine);
                }
            }
            for (Patient patient : new Patient[]{first, second}) {
                if (patient.getId() != null && DB.patients().findById(patient.getId()) != null) {
                    DB.patients().remove(patient);
                }
            }
        }
    }

    @Test
    public void dailyRegenerationPreservesHistoricalDecisionsAndPatients() {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        LocalDate today = LocalDate.now();
        Patient first = new Patient();
        first.setCode("ci-history-a-" + System.nanoTime());
        first.setName("Synthetic history patient A");
        Patient second = new Patient();
        second.setCode("ci-history-b-" + System.nanoTime());
        second.setName("Synthetic history patient B");
        List<EventInstance> created = new ArrayList<>();
        try {
            DB.patients().save(first);
            DB.patients().save(second);
            assertNotNull(first.getId());
            assertNotNull(second.getId());

            // Three ages cover the exact yesterday cutoff, an older rolling
            // agenda occurrence, and a much older historical decision.
            for (int daysAgo : new int[]{1, 2, 30}) {
                DateTime when = today.minusDays(daysAgo)
                        .toDateTimeAtStartOfDay().plusHours(12);
                for (int status = 0; status < 3; status++) {
                    EventInstance event = new EventInstance(
                            when.plusMinutes(status), EventType.MEDICATION_INTAKE);
                    event.setPatient(status == 1 ? first : second);
                    event.setRef(900001000L + daysAgo * 10L + status);
                    event.addParam(EventInstance.PARAM_DOSE, Double.valueOf(status + 1d));
                    if (status == 0) {
                        event.setCompleted(true);
                        event.setCompletedAt(when.plusMinutes(13));
                    } else if (status == 1) {
                        event.setCancelled(true);
                        event.setCompletedAt(when.plusMinutes(14));
                    }
                    DB.eventInstances().save(event);
                    assertNotNull(event.getId());
                    created.add(event);
                }
            }

            // The daily listener invokes both old and far-future pruning,
            // followed by regeneration of the current schedule window.
            new MedicationIntakeReceiver().onBeforeUpdate(context, today);

            for (int i = 0; i < created.size(); i++) {
                EventInstance before = created.get(i);
                int age = new int[]{1, 2, 30}[i / 3];
                int status = i % 3;
                EventInstance persisted = DB.eventInstances().findById(before.getId());
                if (status == 2 && age >= 2) {
                    assertNull("Only obsolete unhandled agenda occurrences are pruned",
                            persisted);
                    continue;
                }
                assertNotNull("Taken, cancelled and still-current doses survive regeneration",
                        persisted);
                assertEquals(before.getType(), persisted.getType());
                assertEquals(before.getTime(), persisted.getTime());
                assertEquals(before.getPatient().getId(), persisted.getPatient().getId());
                assertEquals(before.getRef(), persisted.getRef());
                assertEquals(before.getStringParam(EventInstance.PARAM_DOSE),
                        persisted.getStringParam(EventInstance.PARAM_DOSE));
                assertEquals(before.completed(), persisted.completed());
                assertEquals(before.cancelled(), persisted.cancelled());
                if (status == 0 || status == 1) {
                    assertEquals("Historical completion timestamp cannot be regenerated",
                            before.completedAt(), persisted.completedAt());
                }
            }
            assertFalse("The oldest pending occurrence must not be invented again",
                    DB.eventInstances().exists(EventType.MEDICATION_INTAKE,
                            today.minusDays(30).toDateTimeAtStartOfDay().plusHours(12)
                                    .plusMinutes(2),
                            second, false));
        } finally {
            for (EventInstance event : created) {
                if (event.getId() != null
                        && DB.eventInstances().findById(event.getId()) != null) {
                    DB.eventInstances().remove(event);
                }
            }
            if (first.getId() != null && DB.patients().findById(first.getId()) != null) {
                DB.patients().remove(first);
            }
            if (second.getId() != null && DB.patients().findById(second.getId()) != null) {
                DB.patients().remove(second);
            }
        }
    }
}
