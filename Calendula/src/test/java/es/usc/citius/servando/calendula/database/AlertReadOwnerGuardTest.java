/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.database;

import org.junit.Test;

import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.PatientAlert;
import es.usc.citius.servando.calendula.persistence.Presentation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** No SQLite access: validate the cross-owner read rejection policy. */
public class AlertReadOwnerGuardTest {
    @Test
    public void onlyExactMedicineAndPatientPairCanProduceAnAlert() {
        Patient owner = new Patient();
        owner.setId(11001L);
        Medicine medicine = new Medicine("Synthetic", Presentation.PILLS);
        medicine.setId(11002L);
        medicine.setPatient(owner);
        PatientAlert alert = new PatientAlert();
        alert.setPatient(owner);
        alert.setMedicine(medicine);

        assertTrue(PatientAlertDao.belongsToMedicineOwner(alert, 11002L, 11001L));
        assertFalse(PatientAlertDao.belongsToMedicineOwner(alert, 11003L, 11001L));
        assertFalse(PatientAlertDao.belongsToMedicineOwner(alert, 11002L, 11003L));
        assertFalse(PatientAlertDao.belongsToMedicineOwner(alert, null, 11001L));
        assertFalse(PatientAlertDao.belongsToMedicineOwner(alert, 11002L, null));
        assertFalse(PatientAlertDao.belongsToMedicineOwner(null, 11002L, 11001L));
    }

    @Test
    public void orphanedOrMismatchedAlertIsNotAuthorizedByMedicineIdOnly() {
        Patient owner = new Patient();
        owner.setId(11001L);
        Patient stranger = new Patient();
        stranger.setId(11004L);
        Medicine medicine = new Medicine("Synthetic", Presentation.PILLS);
        medicine.setId(11002L);
        medicine.setPatient(owner);
        PatientAlert alert = new PatientAlert();
        alert.setMedicine(medicine);
        assertFalse(PatientAlertDao.belongsToMedicineOwner(alert, 11002L, 11001L));

        alert.setPatient(stranger);
        assertFalse(PatientAlertDao.belongsToMedicineOwner(alert, 11002L, 11001L));
        alert.setPatient(owner);
        alert.setMedicine(null);
        assertFalse(PatientAlertDao.belongsToMedicineOwner(alert, 11002L, 11001L));
    }
}
