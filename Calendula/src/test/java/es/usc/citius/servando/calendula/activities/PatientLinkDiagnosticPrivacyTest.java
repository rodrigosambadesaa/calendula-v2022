package es.usc.citius.servando.calendula.activities;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PatientLinkDiagnosticPrivacyTest {
    @Test
    public void qrLinkDiagnosticNeverLeaksNameIdOrSecret() {
        PatientDetailActivity.PatientLinkWrapper link =
                new PatientDetailActivity().new PatientLinkWrapper();
        link.name = "private synthetic patient name";
        link.id = "synthetic-profile-127";
        link.token = "synthetic-link-secret";
        String diagnostic = link.toString();
        assertFalse(diagnostic.contains(link.name));
        assertFalse(diagnostic.contains(link.id));
        assertFalse(diagnostic.contains(link.token));
        assertTrue(diagnostic.contains("hasToken=true"));
        assertTrue(diagnostic.contains("hasName=true"));
    }
}
