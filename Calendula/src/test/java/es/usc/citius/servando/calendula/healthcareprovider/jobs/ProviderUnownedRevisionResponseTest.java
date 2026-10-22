package es.usc.citius.servando.calendula.healthcareprovider.jobs;

import org.junit.Test;
import static org.junit.Assert.*;

/** Synthetic revision tokens; no provider network requests or patient records. */
public class ProviderUnownedRevisionResponseTest {
    @Test
    public void cannotAcknowledgeUnchangedResponseWhenNoTrustedRevisionWasSent() {
        for (String invalid : new String[]{null, "", " ", "null", " NULL ", "bad\nrevision"}) {
            assertFalse(UpdateMedicationHelper.canAcceptUnchangedResponse(invalid));
        }
    }

    @Test
    public void validOpaqueRevisionCanBeAcknowledgedAsUnchanged() {
        assertTrue(UpdateMedicationHelper.canAcceptUnchangedResponse("v2026-10#opa que"));
    }
}
