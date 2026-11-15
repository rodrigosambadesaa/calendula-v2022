/* Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util.alerts;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.persistence.alerts.AllergyPatientAlert;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pure identity tests: no live medical data, patient DB, or platform alarms.
 * A restored SQLite row number is not proof of the previous clinical owner.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AlertStableOwnerGuardTest {
    private static Patient patient(long id, String code) {
        Patient p = new Patient();
        p.setId(id);
        p.setCode(code);
        return p;
    }

    private static AllergyPatientAlert alert(
            long alertId, long medicineId, Patient patient) {
        Medicine medicine = new Medicine("Synthetic medicine", Presentation.PILLS);
        medicine.setId(medicineId);
        medicine.setPatient(patient);
        AllergyPatientAlert a = new AllergyPatientAlert();
        a.setId(alertId);
        a.setLevel(1);
        a.setPatient(patient);
        a.setMedicine(medicine);
        return a;
    }

    @Test
    public void unchangedPatientCodeAndMedicineIdentityAreRequiredForDeletion() {
        Patient original = patient(901L, "ci-stable-owner");
        AllergyPatientAlert before = alert(3001L, 411L, original);
        assertTrue(AlertManager.sameAlertIdentity(
                before, alert(3001L, 411L, patient(901L, "ci-stable-owner"))));
        assertFalse(AlertManager.sameAlertIdentity(
                before, alert(3001L, 411L, patient(901L, "ci-other-owner"))));
        assertFalse(AlertManager.sameAlertIdentity(
                before, alert(3001L, 411L, patient(902L, "ci-stable-owner"))));
        assertFalse(AlertManager.sameAlertIdentity(
                before, alert(3001L, 412L, patient(901L, "ci-stable-owner"))));
        assertFalse(AlertManager.sameAlertIdentity(
                before, alert(3002L, 411L, patient(901L, "ci-stable-owner"))));
    }

    @Test
    public void ownerlessLegacyAlertCannotAuthorizeMutation() {
        AllergyPatientAlert valid = alert(
                3001L, 411L, patient(901L, "ci-stable-owner"));
        assertFalse(AlertManager.sameAlertIdentity(
                valid, alert(3001L, 411L, patient(901L, null))));
        assertFalse(AlertManager.sameAlertIdentity(
                valid, alert(3001L, 411L, patient(901L, " "))));
        assertFalse(AlertManager.sameAlertIdentity(null, valid));
        assertFalse(AlertManager.sameAlertIdentity(valid, null));
    }
}
