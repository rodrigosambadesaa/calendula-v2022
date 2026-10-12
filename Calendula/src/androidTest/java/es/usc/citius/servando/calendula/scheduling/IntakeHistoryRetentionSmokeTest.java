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
