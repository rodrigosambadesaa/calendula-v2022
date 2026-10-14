/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.events.GetExtraInfoEvent;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Real Android SQLite and preference transition with disposable synthetic
 * patients. This does not contact a healthcare provider or download PDFs.
 */
@RunWith(AndroidJUnit4.class)
public class ProviderDocumentOwnerSmokeTest {
    @Test
    public void switchingActivePatientInvalidatesOldProviderPdfResponse() throws Exception {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Patient previous = DB.patients().getActive(context);
        String unique = Long.toString(System.nanoTime());
        Patient first = new Patient();
        first.setCode("ci-provider-pdf-A-" + unique);
        first.setName("Synthetic PDF patient A");
        Patient second = new Patient();
        second.setCode("ci-provider-pdf-B-" + unique);
        second.setName("Synthetic PDF patient B");
        try {
            DB.patients().create(first);
            DB.patients().create(second);
            assertNotNull(first.getId());
            assertNotNull(second.getId());

            DB.patients().setActive(first);
            Long originalId = DB.patients().getActive(context).getId();
            GetExtraInfoEvent oldResponse = new GetExtraInfoEvent(
                    GetExtraInfoEvent.Status.SUCCESS, "/cache/synthetic.pdf", originalId, 192);
            assertTrue(ProviderDocumentGuard.canAcceptResponse(
                    originalId, DB.patients().getActive(context).getId(),
                    oldResponse.getPatientId(), 192, oldResponse.getJobId()));

            DB.patients().setActive(second);
            Long newActiveId = DB.patients().getActive(context).getId();
            assertEquals(second.getId(), newActiveId);
            assertFalse("A delayed PDF for patient A cannot appear for patient B",
                    ProviderDocumentGuard.canAcceptResponse(
                            originalId, newActiveId, oldResponse.getPatientId(), 192,
                            oldResponse.getJobId()));
            assertFalse("The new patient's viewer cannot accept patient A's PDF",
                    ProviderDocumentGuard.canAcceptResponse(
                            newActiveId, newActiveId, oldResponse.getPatientId(), 192,
                            oldResponse.getJobId()));
            assertFalse("Legacy ownerless success events must fail closed",
                    ProviderDocumentGuard.canAcceptResponse(newActiveId, newActiveId, null,
                            192, oldResponse.getJobId()));
        } finally {
            if (previous != null && previous.getId() != null
                    && DB.patients().findById(previous.getId()) != null) {
                DB.patients().setActive(previous);
            } else {
                PreferenceUtils.edit().remove(PreferenceKeys.PATIENTS_ACTIVE.key()).commit();
            }
            for (Patient patient : new Patient[]{first, second}) {
                if (patient.getId() != null
                        && DB.patients().findById(patient.getId()) != null) {
                    DB.patients().remove(patient);
                }
            }
        }
    }
}
