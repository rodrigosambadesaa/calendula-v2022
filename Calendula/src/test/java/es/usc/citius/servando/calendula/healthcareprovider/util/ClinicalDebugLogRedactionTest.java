/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.healthcareprovider.util;

import org.dmfs.rfc5545.recur.Freq;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import java.util.HashMap;
import java.util.Map;

import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.scheduling.model.recur.RecurringEvent;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

/** Debug builds must not leak a patient's quantities into Logcat. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ClinicalDebugLogRedactionTest {
    @Test
    public void mappingScheduleDoesNotLogIndividualDosageValues() {
        final String sensitiveQuantity = "123.456789";
        Patient patient = new Patient();
        patient.setId(98761234L);
        Medicine medicine = new Medicine("Synthetic private prescription", Presentation.PILLS);
        medicine.setPatient(patient);

        Map<Long, Double> doses = new HashMap<>();
        doses.put(Schedule.DEFAULT_DOSAGE_KEY, Double.parseDouble(sensitiveQuantity));
        RecurringEvent.Builder recurrence = new RecurringEvent.Builder();
        recurrence.repeatEvery(8, Freq.HOURLY);

        // Robolectric exposes an immutable Logcat snapshot. Compare only
        // messages emitted by this call; never mutate ShadowLog.getLogs().
        final int previousCount = ShadowLog.getLogs().size();
        Schedule mapped = ScheduleCreator.create(recurrence, doses, medicine);
        assertNotNull(mapped);
        java.util.List<ShadowLog.LogItem> recorded = ShadowLog.getLogs();
        for (int index = previousCount; index < recorded.size(); index++) {
            String message = recorded.get(index).msg == null ? "" : recorded.get(index).msg;
            assertFalse("Debug output must not include a patient's exact dose quantity",
                    message.contains(sensitiveQuantity));
            assertFalse("Debug output must not print patient dosage maps",
                    message.contains(doses.toString()));
        }
    }
}
