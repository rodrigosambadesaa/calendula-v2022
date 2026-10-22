package es.usc.citius.servando.calendula.healthcareprovider.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.healthcareprovider.model.ActiveMedVO;
import es.usc.citius.servando.calendula.healthcareprovider.model.ActiveMedVisualizationType;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedEntity;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedType;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DosageEntity;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DosageType;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;
import es.usc.citius.servando.calendula.util.ProviderRevisionGuard;

import static org.junit.Assert.*;

/**
 * Real SQLite regression with synthetic provider medications only.
 * No user treatment, stock, reminder or confirmed intake is used.
 */
@RunWith(AndroidJUnit4.class)
public class EmptyFhirSnapshotReconciliationSmokeTest {
    @Test
    public void validEmptySnapshotRetiresOnlySelectedPatientsProviderMedication()
            throws Exception {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Patient previous = DB.patients().getActive(context);
        SharedPreferences preferences = PreferenceUtils.instance().preferences();
        String hashKey = PreferenceKeys.REMOTE_LAST_HASH.key();
        String ownerKey = PreferenceKeys.REMOTE_LAST_HASH_OWNER.key();
        Object oldHash = preferences.getAll().get(hashKey);
        Object oldOwner = preferences.getAll().get(ownerKey);
        String nonce = Long.toString(System.nanoTime());
        Patient a = patient("a-" + nonce);
        Patient b = patient("b-" + nonce);
        DosageEntity doseA = dosage();
        DosageEntity doseB = dosage();
        ActiveMedEntity medA = med("ci-empty-med-a-" + nonce, a, doseA);
        ActiveMedEntity medB = med("ci-empty-med-b-" + nonce, b, doseB);
        try {
            assertEquals(1, DB.patients().create(a));
            assertEquals(1, DB.patients().create(b));
            assertEquals(1, DB.healthcareProviderDB().dosages().create(doseA));
            assertEquals(1, DB.healthcareProviderDB().dosages().create(doseB));
            assertEquals(1, DB.healthcareProviderDB().activeMeds().create(medA));
            assertEquals(1, DB.healthcareProviderDB().activeMeds().create(medB));

            DB.patients().setActive(a);
            String firstRevision = "ci-empty-fhir-revision-" + nonce;
            ActiveMedUpdater.INSTANCE.onNewMeds(
                    firstRevision, Collections.<ActiveMedVO>emptyList(), context, false);

            assertNull("Authoritatively absent medication must be retired",
                    DB.healthcareProviderDB().activeMeds().findById(medA.getId()));
            assertNotNull("Another patient's provider medication must remain untouched",
                    DB.healthcareProviderDB().activeMeds().findById(medB.getId()));
            assertNotNull("Other patient's dosage must remain untouched",
                    DB.healthcareProviderDB().dosages().findById(doseB.getId()));
            assertEquals(firstRevision,
                    ProviderRevisionGuard.lastRevisionForPatient(a, a.getCode()));
            assertNull(ProviderRevisionGuard.lastRevisionForPatient(b, b.getCode()));

            // A repeated authoritative empty snapshot must be idempotent.
            String secondRevision = firstRevision + "-repeat";
            ActiveMedUpdater.INSTANCE.onNewMeds(
                    secondRevision, Collections.<ActiveMedVO>emptyList(), context, false);
            assertEquals(secondRevision,
                    ProviderRevisionGuard.lastRevisionForPatient(a, a.getCode()));
            assertNotNull(DB.healthcareProviderDB().activeMeds().findById(medB.getId()));
        } finally {
            SharedPreferences.Editor e = preferences.edit();
            restore(e, hashKey, oldHash);
            restore(e, ownerKey, oldOwner);
            assertTrue(e.commit());
            if (previous != null && previous.getId() != null
                    && DB.patients().findById(previous.getId()) != null) {
                DB.patients().setActive(previous);
            } else {
                PreferenceUtils.edit().remove(PreferenceKeys.PATIENTS_ACTIVE.key()).commit();
            }
            for (ActiveMedEntity med : new ActiveMedEntity[]{medA, medB}) {
                if (med.getId() != null
                        && DB.healthcareProviderDB().activeMeds().findById(med.getId()) != null) {
                    DB.healthcareProviderDB().activeMeds().deleteById(med.getId());
                }
            }
            for (DosageEntity d : new DosageEntity[]{doseA, doseB}) {
                if (d.getId() != null
                        && DB.healthcareProviderDB().dosages().findById(d.getId()) != null) {
                    DB.healthcareProviderDB().dosages().deleteById(d.getId());
                }
            }
            for (Patient p : new Patient[]{a, b}) {
                if (p.getId() != null && DB.patients().findById(p.getId()) != null) {
                    DB.patients().deleteById(p.getId());
                }
            }
        }
    }

    private static Patient patient(String code) {
        Patient patient = new Patient();
        patient.setCode("ci-fhir-owner-" + code);
        patient.setName("Synthetic empty FHIR test owner");
        return patient;
    }

    private static DosageEntity dosage() {
        DosageEntity dose = new DosageEntity();
        dose.setType(DosageType.AS_NEEDED);
        return dose;
    }

    private static ActiveMedEntity med(String code, Patient p, DosageEntity dose) {
        ActiveMedEntity item = new ActiveMedEntity();
        item.setCode(code);
        item.setPatient(p);
        item.setType(ActiveMedType.DCPF);
        item.setVisualizationType(ActiveMedVisualizationType.INSULIN_TYPE);
        item.setDefaultDisplay("Synthetic official medication");
        item.setDosage(dose);
        return item;
    }

    private static void restore(SharedPreferences.Editor e, String key, Object value) {
        if (value == null) e.remove(key);
        else if (value instanceof String) e.putString(key, (String) value);
        else if (value instanceof Integer) e.putInt(key, (Integer) value);
        else if (value instanceof Long) e.putLong(key, (Long) value);
        else if (value instanceof Boolean) e.putBoolean(key, (Boolean) value);
        else if (value instanceof Float) e.putFloat(key, (Float) value);
        else throw new AssertionError("Unexpected preference type");
    }
}
