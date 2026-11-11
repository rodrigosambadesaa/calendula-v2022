package es.usc.citius.servando.calendula.fragments;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedEntity;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedType;
import org.joda.time.DateTime;
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

    @Test
    public void restoredIdWithDifferentStableOwnerCannotApplyOldDispensation() {
        Patient original = patient(101L);
        Patient replacement = patient(101L);
        replacement.setCode("ci-dispensation-replacement");

        ActiveMedEntity requested = medicine(201L, original);
        ActiveMedEntity displayed = medicine(201L, patient(101L));
        ActiveMedEntity persisted = medicine(201L, patient(101L));
        assertFalse("A restored patient ID is not an authorization for stale dispensations",
                ProviderDispensationRenderGuard.canApply(
                        requested, displayed, persisted, replacement));
        assertFalse("One replaced displayed record invalidates the async result",
                ProviderDispensationRenderGuard.canApply(
                        requested, medicine(201L, replacement), persisted, original));
        assertFalse("One replaced current provider record invalidates the async result",
                ProviderDispensationRenderGuard.canApply(
                        requested, displayed, medicine(201L, replacement), original));
        assertTrue(ProviderDispensationRenderGuard.canApply(
                requested, displayed, persisted, original));
    }

    @Test
    public void unidentifiedOwnerCannotApplyProviderDispensations() {
        Patient owner = patient(101L);
        ActiveMedEntity valid = medicine(201L, owner);
        Patient missing = patient(101L);
        missing.setCode(null);
        assertFalse(ProviderDispensationRenderGuard.canApply(
                valid, valid, valid, missing));
        missing.setCode(" ");
        assertFalse(ProviderDispensationRenderGuard.canApply(
                valid, valid, valid, missing));
        missing.setCode("");
        assertFalse(ProviderDispensationRenderGuard.canApply(
                medicine(201L, missing), valid, valid, owner));
        assertFalse(ProviderDispensationRenderGuard.canApply(
                valid, medicine(201L, missing), valid, owner));
        assertFalse(ProviderDispensationRenderGuard.canApply(
                valid, valid, medicine(201L, missing), owner));
    }

    @Test
    public void changedMedicationOrRevisionCannotConsumeOldAsynchronousDispensation() {
        Patient owner = patient(101L);
        ActiveMedEntity requested = medicine(201L, owner);
        ActiveMedEntity displayed = medicine(201L, patient(101L));
        ActiveMedEntity persisted = medicine(201L, patient(101L));
        assertTrue(ProviderDispensationRenderGuard.canApply(
                requested, displayed, persisted, owner));

        displayed.setCode("ci-different-drug-same-row-id");
        assertFalse("A restored provider ID cannot represent a new drug",
                ProviderDispensationRenderGuard.canApply(
                        requested, displayed, persisted, owner));
        displayed.setCode(requested.getCode());
        persisted.setType(ActiveMedType.NATIONAL_CODE);
        assertFalse("A changed code type invalidates previous dispensations",
                ProviderDispensationRenderGuard.canApply(
                        requested, displayed, persisted, owner));
        persisted.setType(ActiveMedType.DCPF);

        DateTime updated = DateTime.now().withMillisOfSecond(0);
        persisted.setLastUpdated(updated);
        assertFalse("A different provider revision requires a fresh async result",
                ProviderDispensationRenderGuard.canApply(
                        requested, displayed, persisted, owner));
        requested.setLastUpdated(updated);
        displayed.setLastUpdated(updated);
        assertTrue("Identical current medication revision may apply",
                ProviderDispensationRenderGuard.canApply(
                        requested, displayed, persisted, owner));

        requested.setCode(" ");
        assertFalse("An unidentified official drug is never a valid async match",
                ProviderDispensationRenderGuard.canApply(
                        requested, displayed, persisted, owner));
    }

    private static Patient patient(Long id) {
        Patient patient = new Patient();
        patient.setId(id);
        patient.setCode(id == null ? null : "ci-dispensation-owner-" + id);
        return patient;
    }

    private static ActiveMedEntity medicine(Long id, Patient patient) {
        ActiveMedEntity official = new ActiveMedEntity();
        official.setId(id);
        official.setPatient(patient);
        official.setType(ActiveMedType.DCPF);
        official.setCode(id == null ? null : "ci-provider-medicine-" + id);
        return official;
    }
}
