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

/** No network, scheduler, patient or medication database is used. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PeriodicMedicationJobIdRestoreTest {
    @Test
    public void validPersistedAndroidJobIdIsReused() {
        try (MockedStatic<PreferenceUtils> preferences = mockStatic(PreferenceUtils.class)) {
            preferences.when(() -> PreferenceUtils.getInt(
                    PreferenceKeys.REMOTE_PERIODIC_JOB_ID, -1)).thenReturn(4242);
            assertEquals(4242, UpdateMedicationFromServiceJob.savedPeriodicJobIdOrMissing());
        }
    }

    @Test
    public void negativeOrZeroIdAlwaysRequiresReconstruction() {
        for (int invalid : new int[]{0, -1, -42, Integer.MIN_VALUE}) {
            try (MockedStatic<PreferenceUtils> preferences = mockStatic(PreferenceUtils.class)) {
                preferences.when(() -> PreferenceUtils.getInt(
                        PreferenceKeys.REMOTE_PERIODIC_JOB_ID, -1)).thenReturn(invalid);
                assertEquals(-1, UpdateMedicationFromServiceJob.savedPeriodicJobIdOrMissing());
            }
        }
    }

    @Test
    public void restoredStringOrBooleanDoesNotCrashSyncStartup() {
        try (MockedStatic<PreferenceUtils> preferences = mockStatic(PreferenceUtils.class)) {
            preferences.when(() -> PreferenceUtils.getInt(
                    PreferenceKeys.REMOTE_PERIODIC_JOB_ID, -1))
                    .thenThrow(new ClassCastException("Synthetic restored String"));
            assertEquals(-1, UpdateMedicationFromServiceJob.savedPeriodicJobIdOrMissing());
        }
    }
}
