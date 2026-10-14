/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

import org.junit.Test;
import org.mockito.MockedStatic;

import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mockStatic;

public class ProviderPreferenceReaderTest {
    @Test
    public void wronglyTypedUpdateStatusDoesNotCrashOrLeaveRefreshSpinnerRunning() {
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getString(
                    PreferenceKeys.REMOTE_UPDATE_STATUS, ""))
                    .thenThrow(new ClassCastException("restored boolean"));
            assertEquals("", ProviderPreferenceReader.read(
                    PreferenceKeys.REMOTE_UPDATE_STATUS, ""));
        }
    }

    @Test
    public void corruptLastUpdateAllowsManualRefreshAndDisplaysNoLastCheckedTime() {
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getString(
                    PreferenceKeys.REMOTE_LAST_GOOD_UPDATE_DATE, ""))
                    .thenThrow(new ClassCastException("restored integer"));
            prefs.when(() -> PreferenceUtils.getString(
                    PreferenceKeys.REMOTE_LAST_GOOD_UPDATE_DATE, null))
                    .thenThrow(new ClassCastException("restored integer"));
            assertEquals("", ProviderPreferenceReader.read(
                    PreferenceKeys.REMOTE_LAST_GOOD_UPDATE_DATE, ""));
            assertNull(ProviderPreferenceReader.read(
                    PreferenceKeys.REMOTE_LAST_GOOD_UPDATE_DATE, null));
        }
    }

    @Test
    public void validUpdateMetadataIsPreservedUnchanged() {
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getString(
                    PreferenceKeys.REMOTE_LAST_GOOD_UPDATE_DATE, ""))
                    .thenReturn("2026-10-14T13:00:00Z");
            assertEquals("2026-10-14T13:00:00Z",
                    ProviderPreferenceReader.read(
                            PreferenceKeys.REMOTE_LAST_GOOD_UPDATE_DATE, ""));
        }
    }
}
