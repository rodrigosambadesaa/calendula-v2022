/*
 * Calendula - An assistant for personal medication management.
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
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.persistence.alerts.AllergyPatientAlert;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pure identity policy tests using synthetic, never-persisted fixtures.
 * SQLite and real Android integration is independently covered by
 * AlertDeletionIdentitySmokeTest on API 23/33/36/37.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AlertManagerStableOwnerTest {
    private static Patient patient(long id, String code) {
        Patient owner = new Patient();
        owner.setId(id);
        owner.setCode(code);
        return owner;
    }

    private static AllergyPatientAlert alert(long id, Patient owner) {
        Medicine medicine = new Medicine("Synthetic medication", Presentation.PILLS);
        medicine.setId(80012L);
        medicine.setPatient(owner);
        AllergyPatientAlert result = new AllergyPatientAlert();
        result.setId(id);
        result.setPatient(owner);
        result.setMedicine(medicine);
        result.setLevel(1);
        return result;
    }

    private static Schedule schedule(long id, Medicine med, Patient owner) {
        Schedule result = new Schedule(med);
        result.setId(id);
        result.setPatient(owner);
        return result;
    }

    @Test
    public void blockingHighAlertMustMatchScheduleMedicineAndDurableOwner() {
        Patient original = patient(60012L, "synthetic-original-owner");
        Patient recycled = patient(60012L, "synthetic-replacement-owner");
        Medicine intended = medicine(original);
        assertTrue(AlertManager.sameStableScheduleMedicine(
                intended, schedule(90012L, medicine(original), original)));
        assertFalse("Same numeric patient ID but different owner code",
                AlertManager.sameStableScheduleMedicine(
                        intended, schedule(90012L, medicine(original), recycled)));
        assertFalse("Schedule's medicine link can carry a stale owner",
                AlertManager.sameStableScheduleMedicine(
                        intended, schedule(90012L, medicine(recycled), original)));
        Medicine differentMedicine = medicine(original);
        differentMedicine.setId(80013L);
        assertFalse("A different linked medicine cannot be blocked",
                AlertManager.sameStableScheduleMedicine(
                        intended, schedule(90012L, differentMedicine, original)));
        assertFalse(AlertManager.sameStableScheduleMedicine(
                intended, schedule(90012L, medicine(original), null)));
        assertFalse(AlertManager.sameStableScheduleMedicine(
                intended, schedule(0L, medicine(original), original)));
    }

    @Test
    public void identicalPersistedOwnerAndMedicineAreAccepted() {
        AllergyPatientAlert selected = alert(70012L,
                patient(60012L, "synthetic-live-owner"));
        AllergyPatientAlert persisted = alert(70012L,
                patient(60012L, "synthetic-live-owner"));
        assertTrue(AlertManager.sameAlertIdentity(selected, persisted));
    }

    @Test
    public void recycledNumericPatientIdCannotAuthorizeOldAlertRemoval() {
        AllergyPatientAlert oldSnapshot = alert(70012L,
                patient(60012L, "synthetic-previous-owner"));
        AllergyPatientAlert live = alert(70012L,
                patient(60012L, "synthetic-current-owner"));
        assertFalse(AlertManager.sameAlertIdentity(oldSnapshot, live));
        assertFalse(AlertManager.sameAlertIdentity(live, oldSnapshot));
    }

    @Test
    public void missingOrChangedOwnerIsNeverValidDeletionAuthority() {
        AllergyPatientAlert live = alert(70012L,
                patient(60012L, "synthetic-current-owner"));
        assertFalse(AlertManager.sameAlertIdentity(
                alert(70012L, patient(60012L, null)), live));
        assertFalse(AlertManager.sameAlertIdentity(
                alert(70012L, patient(60012L, "  ")), live));
        assertFalse(AlertManager.sameAlertIdentity(
                alert(70012L, patient(60013L, "synthetic-current-owner")), live));
        assertFalse(AlertManager.sameAlertIdentity(null, live));
        assertFalse(AlertManager.sameAlertIdentity(live, null));
    }
}
