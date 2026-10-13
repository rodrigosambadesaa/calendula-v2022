/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util.alerts;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.persistence.alerts.AllergyPatientAlert;

import static org.junit.Assert.*;

/**
 * Real SQLite identity tests using disposable patients and medicines only.
 * Never creates treatment schedules, OS alarms or real clinical records.
 */
@RunWith(AndroidJUnit4.class)
public class AlertDeletionIdentitySmokeTest {

    @Test
    public void staleOrCrossPatientAlertCannotDeletePersistedAlert() throws Exception {
        assertTrue(DB.initialized);
        final String suffix = Long.toString(System.nanoTime());
        Patient owner = new Patient();
        owner.setName("Synthetic alert owner");
        owner.setCode("ci-alert-owner-" + suffix);
        Patient other = new Patient();
        other.setName("Synthetic unrelated owner");
        other.setCode("ci-alert-other-" + suffix);
        Medicine first = new Medicine("Synthetic protected medicine", Presentation.PILLS);
        Medicine second = new Medicine("Synthetic other medicine", Presentation.PILLS);
        AllergyPatientAlert saved = new AllergyPatientAlert();
        try {
            assertEquals(1, DB.patients().create(owner));
            assertEquals(1, DB.patients().create(other));
            first.setPatient(owner);
            second.setPatient(owner);
            assertEquals(1, DB.medicines().create(first));
            assertEquals(1, DB.medicines().create(second));

            saved.setLevel(1);
            saved.setPatient(owner);
            saved.setMedicine(first);
            assertEquals(1, DB.alerts().create(saved));
            assertNotNull(saved.id());

            AllergyPatientAlert forged = new AllergyPatientAlert();
            forged.setId(saved.id());
            forged.setLevel(saved.getLevel());
            forged.setPatient(other);
            forged.setMedicine(first);
            assertRejected(forged, saved.id());

            forged.setPatient(owner);
            forged.setMedicine(second);
            assertRejected(forged, saved.id());

            forged.setMedicine(first);
            forged.setLevel(3);
            assertRejected(forged, saved.id());

            // An unchanged authoritative alert still deletes exactly its row,
            // and retries do not resurrect an alert or unblock another one.
            AlertManager.removeAlert(saved);
            assertNull(DB.alerts().findById(saved.id()));
            AlertManager.removeAlert(saved);
            assertNull(DB.alerts().findById(saved.id()));
        } finally {
            if (saved.id() != null && DB.alerts().findById(saved.id()) != null) {
                DB.alerts().remove(saved);
            }
            for (Medicine medicine : new Medicine[]{first, second}) {
                if (medicine.getId() != null && DB.medicines().findById(medicine.getId()) != null) {
                    DB.medicines().remove(medicine);
                }
            }
            for (Patient patient : new Patient[]{owner, other}) {
                if (patient.getId() != null && DB.patients().findById(patient.getId()) != null) {
                    DB.patients().remove(patient);
                }
            }
        }
    }

    private static void assertRejected(AllergyPatientAlert forged, Long persistedId) {
        boolean rejected = false;
        try {
            AlertManager.removeAlert(forged);
        } catch (IllegalStateException expected) {
            rejected = true;
        } catch (RuntimeException wrapped) {
            // DB.transaction wraps SQL exceptions, depending on ORMLite version.
            rejected = true;
        }
        assertTrue("Stale identity must not remove another patient's clinical alert", rejected);
        assertNotNull(DB.alerts().findById(persistedId));
    }
}
