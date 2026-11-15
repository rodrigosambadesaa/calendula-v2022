package es.usc.citius.servando.calendula;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.persistence.Patient;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Synthetic profile snapshots: never reuse cached clinical UI across owners.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class HomePagerOwnerIdentityTest {

    private static Patient profile(long id, String code) {
        Patient patient = new Patient();
        patient.setId(id);
        patient.setCode(code);
        return patient;
    }

    @Test
    public void aRecycledSqliteRowMustNotResumePreviousOwnersDashboard() {
        Patient displayed = profile(81001L, "local-synthetic-owner-A");
        Patient replacement = profile(81001L, "local-synthetic-owner-B");
        assertFalse(HomePagerActivity.sameDashboardOwner(displayed, replacement));
        assertFalse(HomePagerActivity.sameDashboardOwner(replacement, displayed));
    }

    @Test
    public void aMatchingStableOwnerCanResumeEvenWithFreshlyLoadedModel() {
        assertTrue(HomePagerActivity.sameDashboardOwner(
                profile(81002L, "local-synthetic-owner-A"),
                profile(81002L, "local-synthetic-owner-A")));
    }

    @Test
    public void missingOrUnidentifiedProfilesMustNeverReuseCachedClinicalUi() {
        Patient verified = profile(81003L, "local-synthetic-owner");
        assertFalse(HomePagerActivity.sameDashboardOwner(null, verified));
        assertFalse(HomePagerActivity.sameDashboardOwner(verified, null));
        assertFalse(HomePagerActivity.sameDashboardOwner(verified, profile(81004L, "local-synthetic-owner")));
        assertFalse(HomePagerActivity.sameDashboardOwner(profile(81003L, null), profile(81003L, null)));
        assertFalse(HomePagerActivity.sameDashboardOwner(profile(81003L, " "), profile(81003L, " ")));
    }
}
