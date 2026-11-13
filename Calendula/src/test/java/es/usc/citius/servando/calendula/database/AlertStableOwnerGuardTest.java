/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.database;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.PatientAlert;
import es.usc.citius.servando.calendula.persistence.Presentation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Disposable synthetic model objects; no database or provider services. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AlertStableOwnerGuardTest {
    private static Patient owner(long id, String code) {
        Patient p = new Patient();
        p.setId(id);
        p.setCode(code);
        return p;
    }

    private static Medicine medicine(long id, Patient owner) {
        Medicine m = new Medicine("Synthetic medicine", Presentation.PILLS);
        m.setId(id);
        m.setPatient(owner);
        return m;
    }

    @Test
    public void exactStableOwnerAndMedicineIdentityAreRequired() {
        Patient original = owner(940000001L, "synthetic-patient-before-restore");
        Medicine medicine = medicine(940000002L, original);
        PatientAlert alert = new PatientAlert();
        alert.setPatient(owner(original.getId(), original.getCode()));
        alert.setMedicine(medicine(medicine.getId(), original));

        assertTrue(PatientAlertDao.belongsToStableMedicineOwner(alert, medicine));
        assertFalse(PatientAlertDao.belongsToStableMedicineOwner(null, medicine));
        assertFalse(PatientAlertDao.belongsToStableMedicineOwner(alert, null));

        alert.setPatient(owner(original.getId(), "synthetic-patient-after-restore"));
        assertFalse("Reused patient row ID must not authorize a clinical warning",
                PatientAlertDao.belongsToStableMedicineOwner(alert, medicine));
        alert.setPatient(owner(original.getId(), null));
        assertFalse(PatientAlertDao.belongsToStableMedicineOwner(alert, medicine));
        alert.setPatient(owner(original.getId(), " "));
        assertFalse(PatientAlertDao.belongsToStableMedicineOwner(alert, medicine));

        alert.setPatient(original);
        alert.setMedicine(medicine(940000003L, original));
        assertFalse(PatientAlertDao.belongsToStableMedicineOwner(alert, medicine));
        alert.setMedicine(medicine(940000002L, original));
        medicine.setPatient(owner(original.getId(), null));
        assertFalse(PatientAlertDao.belongsToStableMedicineOwner(alert, medicine));
    }
}
