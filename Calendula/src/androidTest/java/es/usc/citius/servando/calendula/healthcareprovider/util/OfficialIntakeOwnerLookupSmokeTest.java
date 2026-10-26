package es.usc.citius.servando.calendula.healthcareprovider.util;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.healthcareprovider.model.ActiveMedVisualizationType;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedEntity;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedType;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DosageEntity;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DosageType;
import es.usc.citius.servando.calendula.persistence.Patient;

import static org.junit.Assert.*;

/** Synthetic two-owner lookup fixture for agenda/notification/confirmation readers. */
@RunWith(AndroidJUnit4.class)
public class OfficialIntakeOwnerLookupSmokeTest {
    @Test
    public void wrongPatientCannotLoadOfficialDoseInstructionsByGlobalProviderId()
            throws Exception {
        assertTrue(DB.initialized);
        String nonce = Long.toString(System.nanoTime());
        Patient first = patient("first-" + nonce);
        Patient second = patient("second-" + nonce);
        DosageEntity dosage = new DosageEntity();
        dosage.setType(DosageType.AS_NEEDED);
        ActiveMedEntity provider = new ActiveMedEntity();
        provider.setCode("ci-official-intake-lookup-" + nonce);
        provider.setPatient(first);
        provider.setDosage(dosage);
        provider.setType(ActiveMedType.DCPF);
        provider.setVisualizationType(ActiveMedVisualizationType.INSULIN_TYPE);
        provider.setDefaultDisplay("Synthetic official instructions");
        try {
            assertEquals(1, DB.patients().create(first));
            assertEquals(1, DB.patients().create(second));
            assertEquals(1, DB.healthcareProviderDB().dosages().create(dosage));
            assertEquals(1, DB.healthcareProviderDB().activeMeds().create(provider));
            assertEquals(provider.getId(), DB.healthcareProviderDB().activeMeds()
                    .findByIdForPatient(provider.getId(), first).getId());
            assertNull("Foreign patient must not read provider by global ID",
                    DB.healthcareProviderDB().activeMeds()
                            .findByIdForPatient(provider.getId(), second));
            assertNull("Missing patient is never sufficient to read official instructions",
                    DB.healthcareProviderDB().activeMeds()
                            .findByIdForPatient(provider.getId(), null));
            assertNull(DB.healthcareProviderDB().activeMeds()
                    .findByIdForPatient(null, first));
        } finally {
            if (provider.getId() != null && DB.healthcareProviderDB().activeMeds()
                    .findById(provider.getId()) != null) {
                DB.healthcareProviderDB().activeMeds().deleteById(provider.getId());
            }
            if (dosage.getId() != null && DB.healthcareProviderDB().dosages()
                    .findById(dosage.getId()) != null) {
                DB.healthcareProviderDB().dosages().deleteById(dosage.getId());
            }
            for (Patient owner : new Patient[]{first, second}) {
                if (owner.getId() != null && DB.patients().findById(owner.getId()) != null) {
                    DB.patients().deleteById(owner.getId());
                }
            }
        }
    }

    private static Patient patient(String id) {
        Patient p = new Patient();
        p.setCode("ci-lookup-owner-" + id);
        p.setName("Synthetic official intake lookup owner");
        return p;
    }
}
