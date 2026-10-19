package es.usc.citius.servando.calendula.healthcareprovider.jobs;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

import static org.junit.Assert.*;

/** Real SQLite selected-profile transitions with synthetic identities. */
@RunWith(AndroidJUnit4.class)
public class ProviderLoginPatientIsolationSmokeTest {
    @Test
    public void switchingLocalPatientBlocksOldAuthenticatedSyncIdentity() throws Exception {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Patient previous = DB.patients().getActive(context);
        String suffix = Long.toString(System.nanoTime());
        Patient first = patient("ci-login-subject-A-" + suffix);
        Patient second = patient("ci-login-subject-B-" + suffix);
        try {
            assertEquals(1, DB.patients().create(first));
            assertEquals(1, DB.patients().create(second));
            assertNotNull(first.getId());
            assertNotNull(second.getId());

            DB.patients().setActive(first);
            Long originalId = DB.patients().getActive(context).getId();
            assertTrue(UpdateMedicationHelper.isAuthenticatedPatient(
                    first.getCode(), DB.patients().getActive(context)));

            DB.patients().setActive(second);
            Patient switched = DB.patients().getActive(context);
            assertEquals(second.getId(), switched.getId());
            assertFalse("Another local profile must not use account A provider",
                    UpdateMedicationHelper.isAuthenticatedPatient(first.getCode(), switched));
            assertFalse("Delayed response must not be acknowledged after switch",
                    UpdateMedicationHelper.isSameAuthenticatedPatient(
                            first.getCode(), originalId, switched));
            assertTrue("Valid account B profile may be selected separately",
                    UpdateMedicationHelper.isAuthenticatedPatient(second.getCode(), switched));
        } finally {
            if (previous != null && previous.getId() != null
                    && DB.patients().findById(previous.getId()) != null) {
                DB.patients().setActive(previous);
            } else {
                PreferenceUtils.edit().remove(PreferenceKeys.PATIENTS_ACTIVE.key()).commit();
            }
            for (Patient owner : new Patient[]{first, second}) {
                if (owner.getId() != null && DB.patients().findById(owner.getId()) != null) {
                    DB.patients().remove(owner);
                }
            }
        }
    }

    private static Patient patient(String subject) {
        Patient result = new Patient();
        result.setCode(subject);
        result.setName("Synthetic provider account owner");
        return result;
    }
}
