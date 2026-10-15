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

    @Test
    public void everyBackgroundCanFollowAnyOtherWithoutRepetitionOrBias() {
        for (int previous = 1; previous <= HomeProfileMgr.BG_COUNT; previous++) {
            boolean[] visited = new boolean[HomeProfileMgr.BG_COUNT + 1];
            for (int choice = 0; choice < HomeProfileMgr.BG_COUNT - 1; choice++) {
                int selected = HomeProfileMgr.selectNextBackgroundIndex(previous, choice);
                org.junit.Assert.assertNotEquals(previous, selected);
                org.junit.Assert.assertTrue(selected >= 1
                        && selected <= HomeProfileMgr.BG_COUNT);
                org.junit.Assert.assertFalse("Each alternative must be selected once",
                        visited[selected]);
                visited[selected] = true;
            }
            for (int alternate = 1; alternate <= HomeProfileMgr.BG_COUNT; alternate++) {
                if (alternate != previous) {
                    org.junit.Assert.assertTrue(
                            "Every alternative must be reachable: " + alternate,
                            visited[alternate]);
                }
            }
        }
    }

    @Test
    public void outOfRangeRandomChoiceCannotSelectNonexistentAsset() {
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> HomeProfileMgr.selectNextBackgroundIndex(1, -1));
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> HomeProfileMgr.selectNextBackgroundIndex(1, HomeProfileMgr.BG_COUNT - 1));
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> HomeProfileMgr.selectNextBackgroundIndex(0, 0));
    }
}
