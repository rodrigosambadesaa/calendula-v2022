package es.usc.citius.servando.calendula.healthcareprovider.jobs;

import org.junit.Test;

import es.usc.citius.servando.calendula.persistence.Patient;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Synthetic IDs only: no credentials, health records or remote requests. */
public class ProviderAuthenticatedPatientGuardTest {
    private static Patient patient(Long id, String subject) {
        Patient p = new Patient();
        p.setId(id);
        p.setCode(subject);
        return p;
    }

    @Test
    public void importedPatientMustMatchAuthenticatedLoginSubject() {
        Patient expected = patient(101L, "subject-a");
        assertTrue(UpdateMedicationHelper.isAuthenticatedPatient("subject-a", expected));
        assertFalse(UpdateMedicationHelper.isAuthenticatedPatient("subject-b", expected));
        assertFalse(UpdateMedicationHelper.isAuthenticatedPatient(null, expected));
        assertFalse(UpdateMedicationHelper.isAuthenticatedPatient("", expected));
        assertFalse(UpdateMedicationHelper.isAuthenticatedPatient(" ", expected));
        assertFalse(UpdateMedicationHelper.isAuthenticatedPatient("subject-a", null));
        assertFalse(UpdateMedicationHelper.isAuthenticatedPatient("subject-a", patient(null, "subject-a")));
        assertFalse(UpdateMedicationHelper.isAuthenticatedPatient("subject-a", patient(0L, "subject-a")));
        assertFalse(UpdateMedicationHelper.isAuthenticatedPatient("subject-a", patient(101L, null)));
    }

    @Test
    public void rejectProfileChangeEvenIfProviderIdentifierIsTheSame() {
        assertTrue(UpdateMedicationHelper.isSameAuthenticatedPatient(
                "subject-a", 101L, patient(101L, "subject-a")));
        assertFalse(UpdateMedicationHelper.isSameAuthenticatedPatient(
                "subject-a", 101L, patient(102L, "subject-a")));
        assertFalse(UpdateMedicationHelper.isSameAuthenticatedPatient(
                "subject-a", 101L, patient(102L, "subject-b")));
        assertFalse(UpdateMedicationHelper.isSameAuthenticatedPatient(
                "subject-a", 101L, patient(101L, "subject-b")));
        assertFalse(UpdateMedicationHelper.isSameAuthenticatedPatient(
                "subject-a", null, patient(101L, "subject-a")));
        assertFalse(UpdateMedicationHelper.isSameAuthenticatedPatient(
                "subject-a", 101L, null));
    }
}
