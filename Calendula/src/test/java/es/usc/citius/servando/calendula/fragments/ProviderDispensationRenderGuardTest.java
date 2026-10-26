package es.usc.citius.servando.calendula.fragments;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedEntity;
import es.usc.citius.servando.calendula.persistence.Patient;
import static org.junit.Assert.*;

/** Synthetic owner/record identities only, no private medical data. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ProviderDispensationRenderGuardTest {
    @Test
    public void onlySamePersistedOfficialMedicineForCurrentPatientMayRender() {
        Patient a = patient(101L);
        ActiveMedEntity requested = medicine(201L, a);
        ActiveMedEntity displayed = medicine(201L, patient(101L));
        ActiveMedEntity persisted = medicine(201L, patient(101L));
        assertTrue(ProviderDispensationRenderGuard.canApply(
                requested, displayed, persisted, patient(101L)));
        assertFalse(ProviderDispensationRenderGuard.canApply(
                requested, displayed, persisted, patient(102L)));
        assertFalse(ProviderDispensationRenderGuard.canApply(
                requested, displayed, medicine(201L, patient(102L)), a));
        assertFalse(ProviderDispensationRenderGuard.canApply(
                requested, medicine(202L, a), persisted, a));
        assertFalse(ProviderDispensationRenderGuard.canApply(
                requested, displayed, medicine(203L, a), a));
    }

    @Test
    public void missingOrDeletedProviderAndInvalidSelectedPatientFailClosed() {
        Patient a = patient(101L);
        ActiveMedEntity record = medicine(201L, a);
        assertFalse(ProviderDispensationRenderGuard.canApply(record, record, null, a));
        assertFalse(ProviderDispensationRenderGuard.canApply(null, record, record, a));
        assertFalse(ProviderDispensationRenderGuard.canApply(record, null, record, a));
        assertFalse(ProviderDispensationRenderGuard.canApply(record, record, record, null));
        assertFalse(ProviderDispensationRenderGuard.canApply(
                record, record, record, patient(0L)));
        assertFalse(ProviderDispensationRenderGuard.canApply(
                medicine(null, a), medicine(null, a), medicine(null, a), a));
        assertFalse(ProviderDispensationRenderGuard.canApply(
                medicine(201L, new Patient()), record, record, a));
    }

    private static Patient patient(Long id) {
        Patient patient = new Patient();
        patient.setId(id);
        return patient;
    }

    private static ActiveMedEntity medicine(Long id, Patient patient) {
        ActiveMedEntity official = new ActiveMedEntity();
        official.setId(id);
        official.setPatient(patient);
        return official;
    }
}
