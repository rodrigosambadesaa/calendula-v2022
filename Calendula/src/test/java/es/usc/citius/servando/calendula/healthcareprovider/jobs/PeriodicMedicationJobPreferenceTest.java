/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.healthcareprovider.jobs;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mockStatic;

/** No Evernote job scheduling, HTTP requests or clinical rows are involved. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PeriodicMedicationJobPreferenceTest {
    @Test
    public void restoredWrongTypeIsNotARegisteredPeriodicJob() {
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getInt(
                    PreferenceKeys.REMOTE_PERIODIC_JOB_ID, -1))
                    .thenThrow(new ClassCastException("synthetic old String"));
            assertEquals(-1, UpdateMedicationFromServiceJob.savedPeriodicJobIdOrMissing());
        }
    }

    @Test
    public void invalidSavedIdsMustBeRescheduled() {
        for (int invalid : new int[]{-500, -1, 0}) {
            try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
                prefs.when(() -> PreferenceUtils.getInt(
                        PreferenceKeys.REMOTE_PERIODIC_JOB_ID, -1)).thenReturn(invalid);
                assertEquals(-1, UpdateMedicationFromServiceJob.savedPeriodicJobIdOrMissing());
            }
        }
    }

    @Test
    public void validPersistedJobIdIsPreserved() {
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getInt(
                    PreferenceKeys.REMOTE_PERIODIC_JOB_ID, -1)).thenReturn(82436);
            assertEquals(82436, UpdateMedicationFromServiceJob.savedPeriodicJobIdOrMissing());
        }
    }
}
