/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;

import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class MedicationIntakeSnapshotTest {
    private static EventInstance event(long id, long ref, long patientId, DateTime time,
                                        String dose) {
        Patient patient = new Patient();
        patient.setId(patientId);
        EventInstance event = new EventInstance(time, EventType.MEDICATION_INTAKE);
        event.setId(id);
        event.setRef(ref);
        event.setPatient(patient);
        event.addParam(EventInstance.PARAM_DOSE, dose);
        return event;
    }

    @Test
    public void sameRecordedSetHasSameDigestIndependentOfDaoRowOrder() {
        DateTime when = new DateTime(2026, 10, 11, 15, 0);
        EventInstance first = event(900005001L, 900006001L, 900007001L, when, "2.0");
        EventInstance second = event(900005002L, 900006002L, 900007001L, when, "1.0");
        String original = MedicationIntakeSnapshot.fingerprint(Arrays.asList(first, second));
        assertNotNull(original);
        assertEquals(64, original.length());
        assertEquals(original, MedicationIntakeSnapshot.fingerprint(
                Arrays.asList(second, first)));
        assertFalse("No plaintext dosage must appear in the PendingIntent",
                original.contains("2.0"));
    }

    @Test
    public void addedDoseRevisionOwnerOrStatusChangesFingerprint() {
        DateTime when = new DateTime(2026, 10, 11, 15, 0);
        EventInstance first = event(900005011L, 900006011L, 900007011L, when, "2.0");
        String prior = MedicationIntakeSnapshot.fingerprint(Collections.singletonList(first));
        EventInstance inserted = event(900005012L, 900006012L, 900007011L, when, "1.0");
        assertFalse(prior.equals(MedicationIntakeSnapshot.fingerprint(
                Arrays.asList(first, inserted))));
        first.addParam(EventInstance.PARAM_DOSE, "3.0");
        assertFalse(prior.equals(MedicationIntakeSnapshot.fingerprint(
                Collections.singletonList(first))));
        first.addParam(EventInstance.PARAM_DOSE, "2.0");
        first.setCompleted(true);
        assertFalse(prior.equals(MedicationIntakeSnapshot.fingerprint(
                Collections.singletonList(first))));
        first.setCompleted(false);
        first.getPatient().setId(900007012L);
        assertFalse(prior.equals(MedicationIntakeSnapshot.fingerprint(
                Collections.singletonList(first))));
    }

    @Test
    public void missingIdentityDuplicatesAndMalformedBundlesCannotGenerateSnapshot() {
        DateTime when = new DateTime(2026, 10, 11, 15, 0);
        EventInstance event = event(900005031L, 900006031L, 900007031L, when, "1.0");
        assertNull(MedicationIntakeSnapshot.fingerprint(null));
        assertNull(MedicationIntakeSnapshot.fingerprint(Collections.emptyList()));
        assertNull(MedicationIntakeSnapshot.fingerprint(Arrays.asList(event, event)));
        event.setRef(null);
        assertNull(MedicationIntakeSnapshot.fingerprint(Collections.singletonList(event)));
        event.setRef(900006031L);
        event.getParams().putLong(EventInstance.PARAM_DOSE, 3L);
        assertNull(MedicationIntakeSnapshot.fingerprint(Collections.singletonList(event)));
    }
}
