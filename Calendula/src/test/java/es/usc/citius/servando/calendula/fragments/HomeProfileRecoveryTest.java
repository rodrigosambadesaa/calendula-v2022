/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mockStatic;

/** User-interface-only regression tests; no patients or medication data. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class HomeProfileRecoveryTest {

    @Test
    public void savedImageIndexMustStayWithinBundledAssetBounds() {
        for (int invalid : new int[]{Integer.MIN_VALUE, -1, 0, 9, 100, Integer.MAX_VALUE}) {
            assertEquals(1, HomeProfileMgr.validBackgroundIndex(invalid));
        }
        for (int valid = 1; valid <= HomeProfileMgr.BG_COUNT; valid++) {
            assertEquals(valid, HomeProfileMgr.validBackgroundIndex(valid));
        }
    }

    @Test
    public void invalidImageIndexCannotGenerateUnbundledAssetPath() {
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getInt(
                    PreferenceKeys.HOME_PROFILE_BACKGROUND_INDEX, 1)).thenReturn(999);
            assertEquals("home_bg_1.webp", HomeProfileMgr.getBackgroundPath());
        }
    }

    @Test
    public void restoredWrongTypeBackgroundDoesNotCrashHome() {
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getInt(
                    PreferenceKeys.HOME_PROFILE_BACKGROUND_INDEX, 1))
                    .thenThrow(new ClassCastException("Synthetic restored string"));
            assertEquals("home_bg_1.webp", HomeProfileMgr.getBackgroundPath());
        }
    }

    @Test
    public void homeLabelFallbackDoesNotExposeGarbageOrHideValidNames() {
        assertEquals("Calendula", HomeProfileMgr.validDisplayName(null, "Calendula"));
        assertEquals("Calendula", HomeProfileMgr.validDisplayName("", "Calendula"));
        assertEquals("Calendula", HomeProfileMgr.validDisplayName("  ", "Calendula"));
        assertEquals("Calendula", HomeProfileMgr.validDisplayName(
                new String(new char[300]).replace((char) 0, 'X'), "Calendula"));
        assertEquals("My profile", HomeProfileMgr.validDisplayName(
                "My profile", "Calendula"));
    }
}
