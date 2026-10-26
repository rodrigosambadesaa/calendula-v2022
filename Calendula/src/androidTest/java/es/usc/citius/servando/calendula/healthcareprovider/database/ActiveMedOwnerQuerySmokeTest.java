/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.healthcareprovider.database;

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

/** Two patients can legally have the same official drug code. */
@RunWith(AndroidJUnit4.class)
public class ActiveMedOwnerQuerySmokeTest {
    @Test
    public void sameCodeQueriesReturnOnlyTheirOwnPatientRecord() throws Exception {
        assertTrue(DB.initialized);
        String code = "ci-same-provider-code-" + System.nanoTime();
        Patient a = patient("a");
        Patient b = patient("b");
        DosageEntity aDosage = new DosageEntity();
        DosageEntity bDosage = new DosageEntity();
        aDosage.setType(DosageType.AS_NEEDED);
        bDosage.setType(DosageType.AS_NEEDED);
        ActiveMedEntity aMed = entity(code, a, aDosage);
        ActiveMedEntity bMed = entity(code, b, bDosage);
        org.joda.time.DateTime aUpdated = new org.joda.time.DateTime(2026, 10, 1, 12, 0);
        org.joda.time.DateTime bUpdated = new org.joda.time.DateTime(2026, 10, 20, 18, 30);
        aMed.setLastUpdated(aUpdated);
        bMed.setLastUpdated(bUpdated);
        try {
            assertEquals(1, DB.patients().create(a));
            assertEquals(1, DB.patients().create(b));
            assertEquals(1, DB.healthcareProviderDB().dosages().create(aDosage));
            assertEquals(1, DB.healthcareProviderDB().dosages().create(bDosage));
            assertEquals(1, DB.healthcareProviderDB().activeMeds().create(aMed));
            assertEquals(1, DB.healthcareProviderDB().activeMeds().create(bMed));
            assertNotNull(aMed.getId());
            assertNotNull(bMed.getId());

            ActiveMedEntity first = DB.healthcareProviderDB().activeMeds()
                    .findByCodeForPatient(code, a);
            ActiveMedEntity second = DB.healthcareProviderDB().activeMeds()
                    .findByCodeForPatient(code, b);
            assertNotNull(first);
            assertNotNull(second);
            assertEquals(aMed.getId(), first.getId());
            assertEquals(bMed.getId(), second.getId());
            assertNotEquals(first.getId(), second.getId());
            assertNull(DB.healthcareProviderDB().activeMeds()
                    .findByIdForPatient(aMed.getId(), b));
            assertNull("Confirmation must never retrieve B's official prescription for A",
                    DB.healthcareProviderDB().activeMeds()
                            .findByIdForPatient(bMed.getId(), a));
            assertEquals(aMed.getId(), DB.healthcareProviderDB().activeMeds()
                    .findByIdForPatient(aMed.getId(), a).getId());
            assertEquals(bMed.getId(), DB.healthcareProviderDB().activeMeds()
                    .findByIdForPatient(bMed.getId(), b).getId());
            assertNull(DB.healthcareProviderDB().activeMeds()
                    .findByCodeForPatient(code, null));
            assertNull(DB.healthcareProviderDB().activeMeds()
                    .findByCodeForPatient(null, a));
            // Provider update metadata is just as patient-sensitive as dosage.
            assertEquals(aUpdated, DB.healthcareProviderDB().activeMeds()
                    .findLastUpdateDate(a));
            assertEquals(bUpdated, DB.healthcareProviderDB().activeMeds()
                    .findLastUpdateDate(b));
            assertNull(DB.healthcareProviderDB().activeMeds()
                    .findLastUpdateDate(null));
            assertNull(DB.healthcareProviderDB().activeMeds()
                    .findLastUpdateDate(new Patient()));
            Patient deletedOrForged = patient("not-persisted");
            deletedOrForged.setId(Long.MAX_VALUE);
            assertNull(DB.healthcareProviderDB().activeMeds()
                    .findLastUpdateDate(deletedOrForged));
        } finally {
            for (ActiveMedEntity med : new ActiveMedEntity[]{aMed, bMed}) {
                if (med.getId() != null
                        && DB.healthcareProviderDB().activeMeds().findById(med.getId()) != null)
                    DB.healthcareProviderDB().activeMeds().deleteById(med.getId());
            }
            for (DosageEntity dose : new DosageEntity[]{aDosage, bDosage}) {
                if (dose.getId() != null
                        && DB.healthcareProviderDB().dosages().findById(dose.getId()) != null)
                    DB.healthcareProviderDB().dosages().deleteById(dose.getId());
            }
            for (Patient owner : new Patient[]{a, b}) {
                if (owner.getId() != null && DB.patients().findById(owner.getId()) != null)
                    DB.patients().deleteById(owner.getId());
            }
        }
    }

    @Test
    public void duplicateCodeWithinOnePatientFailsClosed() throws Exception {
        assertTrue(DB.initialized);
        String code = "ci-duplicate-provider-code-" + System.nanoTime();
        Patient owner = patient("duplicate");
        DosageEntity firstDosage = new DosageEntity();
        DosageEntity secondDosage = new DosageEntity();
        firstDosage.setType(DosageType.AS_NEEDED);
        secondDosage.setType(DosageType.AS_NEEDED);
        ActiveMedEntity first = entity(code, owner, firstDosage);
        ActiveMedEntity second = entity(code, owner, secondDosage);
        try {
            assertEquals(1, DB.patients().create(owner));
            assertEquals(1, DB.healthcareProviderDB().dosages().create(firstDosage));
            assertEquals(1, DB.healthcareProviderDB().dosages().create(secondDosage));
            assertEquals(1, DB.healthcareProviderDB().activeMeds().create(first));
            assertEquals(1, DB.healthcareProviderDB().activeMeds().create(second));
            boolean rejected = false;
            try {
                DB.healthcareProviderDB().activeMeds().findByCodeForPatient(code, owner);
            } catch (IllegalStateException expected) {
                rejected = true;
            }
            assertTrue("Ambiguous same-patient provider rows must be rejected", rejected);
            // A rejected lookup must not have deleted or altered either row.
            assertNotNull(DB.healthcareProviderDB().activeMeds().findById(first.getId()));
            assertNotNull(DB.healthcareProviderDB().activeMeds().findById(second.getId()));
        } finally {
            for (ActiveMedEntity med : new ActiveMedEntity[]{first, second}) {
                if (med.getId() != null
                        && DB.healthcareProviderDB().activeMeds().findById(med.getId()) != null)
                    DB.healthcareProviderDB().activeMeds().deleteById(med.getId());
            }
            for (DosageEntity dose : new DosageEntity[]{firstDosage, secondDosage}) {
                if (dose.getId() != null
                        && DB.healthcareProviderDB().dosages().findById(dose.getId()) != null)
                    DB.healthcareProviderDB().dosages().deleteById(dose.getId());
            }
            if (owner.getId() != null && DB.patients().findById(owner.getId()) != null)
                DB.patients().deleteById(owner.getId());
        }
    }

    private static Patient patient(String name) {
        Patient owner = new Patient();
        owner.setCode("ci-med-owner-" + name + "-" + System.nanoTime());
        owner.setName("Synthetic provider patient " + name);
        return owner;
    }

    private static ActiveMedEntity entity(String code, Patient owner, DosageEntity dosage) {
        ActiveMedEntity med = new ActiveMedEntity();
        med.setCode(code);
        med.setPatient(owner);
        med.setType(ActiveMedType.DCPF);
        med.setVisualizationType(ActiveMedVisualizationType.INSULIN_TYPE);
        med.setDefaultDisplay("Synthetic official medication");
        med.setDosage(dosage);
        return med;
    }
}
