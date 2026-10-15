/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mockStatic;

/** Does not perform HTTP requests, authentication or clinical data writes. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class RemoteMedicationPreferenceReaderTest {
    @Test
    public void corruptRestoredSyncStatusDoesNotCrashHome() {
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getString(
                    PreferenceKeys.REMOTE_UPDATE_STATUS, null))
                    .thenThrow(new ClassCastException("Synthetic wrong-type status"));
            assertNull(RemoteMedicationPreferenceReader.readOrDefault(
                    PreferenceKeys.REMOTE_UPDATE_STATUS, null));
        }
    }

    @Test
    public void wrongTypeSummaryDoesNotSuppressLastUpdateDialog() {
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getString(
                    PreferenceKeys.REMOTE_LAST_GOOD_UPDATE_SUMMARY, ""))
                    .thenThrow(new ClassCastException("Synthetic boolean summary"));
            assertEquals("", RemoteMedicationPreferenceReader.readOrDefault(
                    PreferenceKeys.REMOTE_LAST_GOOD_UPDATE_SUMMARY, ""));
        }
    }

    @Test
    public void corruptOrMissingRevisionNeverClaimsIncrementalSync() {
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getString(
                    PreferenceKeys.REMOTE_LAST_HASH, null))
                    .thenThrow(new ClassCastException("Synthetic integer revision"));
            assertNull(RemoteMedicationPreferenceReader.lastRevisionOrNull());
        }
        for (String invalid : new String[]{null, "", "   "}) {
            try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
                prefs.when(() -> PreferenceUtils.getString(
                        PreferenceKeys.REMOTE_LAST_HASH, null)).thenReturn(invalid);
                assertNull(RemoteMedicationPreferenceReader.lastRevisionOrNull());
            }
        }
    }

    @Test
    public void validRevisionAndStatusAreNotRewritten() {
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getString(
                    PreferenceKeys.REMOTE_LAST_HASH, null)).thenReturn("v2026-10");
            prefs.when(() -> PreferenceUtils.getString(
                    PreferenceKeys.REMOTE_UPDATE_STATUS, null)).thenReturn("SUCCESS");
            assertEquals("v2026-10", RemoteMedicationPreferenceReader.lastRevisionOrNull());
            assertEquals("SUCCESS", RemoteMedicationPreferenceReader.readOrDefault(
                    PreferenceKeys.REMOTE_UPDATE_STATUS, null));
        }
    }
}
