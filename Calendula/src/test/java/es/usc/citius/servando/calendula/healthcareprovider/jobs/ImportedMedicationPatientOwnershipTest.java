/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.healthcareprovider.jobs;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedEntity;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedType;
import es.usc.citius.servando.calendula.persistence.Patient;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ImportedMedicationPatientOwnershipTest {
    private Patient patient(long id) {
        Patient patient = new Patient();
        patient.setId(id);
        return patient;
    }

    private ActiveMedEntity imported(Patient owner) {
        ActiveMedEntity treatment = new ActiveMedEntity();
        treatment.setId(10777L);
        treatment.setPatient(owner);
        treatment.setType(ActiveMedType.NATIONAL_CODE);
        treatment.setCode("123456");
        return treatment;
    }

    @Test
    public void importedPrescriptionCannotBeAddedToAnotherPatientsMedicineKit() {
        Patient owner = patient(101L);
        Patient other = patient(202L);
        ActiveMedEntity treatment = imported(owner);

        assertTrue(UpdateMedicationHelper.isValidImportedMedicineOwner(
                treatment, patient(101L)));
        assertFalse(UpdateMedicationHelper.isValidImportedMedicineOwner(treatment, other));
        assertFalse(UpdateMedicationHelper.isValidImportedMedicineOwner(treatment, null));
        assertFalse(UpdateMedicationHelper.isValidImportedMedicineOwner(null, owner));

        treatment.setPatient(patient(202L));
        assertFalse(UpdateMedicationHelper.isValidImportedMedicineOwner(treatment, owner));
    }

    @Test
    public void incompleteProviderIdentityNeverAuthorizesMedicineCreation() {
        Patient owner = patient(101L);
        ActiveMedEntity t = imported(owner);
        t.setId(null);
        assertFalse(UpdateMedicationHelper.isValidImportedMedicineOwner(t, owner));
        t.setId(10777L);
        t.setCode("");
        assertFalse(UpdateMedicationHelper.isValidImportedMedicineOwner(t, owner));
        t.setCode("123456");
        t.setType(null);
        assertFalse(UpdateMedicationHelper.isValidImportedMedicineOwner(t, owner));
        t.setType(ActiveMedType.DCPF);
        assertTrue(UpdateMedicationHelper.isValidImportedMedicineOwner(t, owner));
    }
}
