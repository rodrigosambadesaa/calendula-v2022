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
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;
import static org.junit.Assert.*;

/** Reject corrupted cross-patient local schedule links before treatment deletion. */
@RunWith(AndroidJUnit4.class)
public class CrossPatientFhirRetirementSmokeTest {
    @Test
    public void emptySnapshotCannotRetireTreatmentBoundToAnotherPatientsSchedule()
            throws Exception {
        assertTrue(DB.initialized);
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Patient prior = DB.patients().getActive(ctx);
        SharedPreferences prefs = PreferenceUtils.instance().preferences();
        String revKey = PreferenceKeys.REMOTE_LAST_HASH.key();
        Object originalRevision = prefs.getAll().get(revKey);
        String nonce = Long.toString(System.nanoTime());
        Patient a = patient("a-" + nonce);
        Patient b = patient("b-" + nonce);
        DosageEntity dosage = new DosageEntity();
        dosage.setType(DosageType.AS_NEEDED);
        ActiveMedEntity official = new ActiveMedEntity();
        official.setCode("ci-cross-bound-" + nonce);
        official.setPatient(a);
        official.setDosage(dosage);
        official.setType(ActiveMedType.DCPF);
        official.setVisualizationType(ActiveMedVisualizationType.INSULIN_TYPE);
        official.setDefaultDisplay("Synthetic official record");
        Schedule crossBound = new Schedule();
        crossBound.setPatient(b);
        try {
            assertEquals(1, DB.patients().create(a));
            assertEquals(1, DB.patients().create(b));
            assertEquals(1, DB.healthcareProviderDB().dosages().create(dosage));
            assertEquals(1, DB.healthcareProviderDB().activeMeds().create(official));
            crossBound.setActiveMedId(official.getId());
            assertEquals(1, DB.schedules().create(crossBound));
            DB.patients().setActive(a);
            String rejected = "ci-cross-bound-revision-" + nonce;
            try {
                ActiveMedUpdater.INSTANCE.onNewMeds(rejected,
                        Collections.<ActiveMedVO>emptyList(), ctx, false);
                fail("Cross-owned schedule must reject official medicine retirement");
            } catch (RuntimeException expected) {
                // DB.transaction wraps the original ownership exception in a
                // RuntimeException/SQLException. Check the full cause chain,
                // not only the outer wrapper, so other failures cannot pass.
                Throwable cause = expected;
                boolean ownershipRejected = false;
                while (cause != null) {
                    if (cause instanceof IllegalStateException
                            && cause.getMessage() != null
                            && cause.getMessage().contains("retirement links")) {
                        ownershipRejected = true;
                        break;
                    }
                    cause = cause.getCause();
                }
                assertTrue("Expected cross-patient retirement ownership rejection",
                        ownershipRejected);
            }
            assertNotNull("Official medicine must remain",
                    DB.healthcareProviderDB().activeMeds().findById(official.getId()));
            assertEquals("Foreign schedule must stay linked",
                    official.getId(),
                    DB.schedules().findById(crossBound.getId()).getActiveMedId());
            assertFalse("Failed import cannot advance revision",
                    rejected.equals(prefs.getAll().get(revKey)));
        } finally {
            if (crossBound.getId() != null
                    && DB.schedules().findById(crossBound.getId()) != null)
                DB.schedules().deleteById(crossBound.getId());
            if (official.getId() != null
                    && DB.healthcareProviderDB().activeMeds().findById(official.getId()) != null)
                DB.healthcareProviderDB().activeMeds().deleteById(official.getId());
            if (dosage.getId() != null
                    && DB.healthcareProviderDB().dosages().findById(dosage.getId()) != null)
                DB.healthcareProviderDB().dosages().deleteById(dosage.getId());
            if (prior != null && prior.getId() != null
                    && DB.patients().findById(prior.getId()) != null)
                DB.patients().setActive(prior);
            else PreferenceUtils.edit().remove(PreferenceKeys.PATIENTS_ACTIVE.key()).commit();
            for (Patient p : new Patient[]{a, b})
                if (p.getId() != null && DB.patients().findById(p.getId()) != null)
                    DB.patients().deleteById(p.getId());
            SharedPreferences.Editor restore = prefs.edit();
            if (originalRevision == null) restore.remove(revKey);
            else if (originalRevision instanceof String)
                restore.putString(revKey, (String) originalRevision);
            else if (originalRevision instanceof Integer)
                restore.putInt(revKey, (Integer) originalRevision);
            else throw new AssertionError("Unexpected original revision type");
            assertTrue(restore.commit());
        }
    }
    private static Patient patient(String suffix) {
        Patient patient = new Patient();
        patient.setCode("ci-cross-fhir-owner-" + suffix);
        patient.setName("Synthetic FHIR owner");
        return patient;
    }
}
