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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mockStatic;

/** UI-only preference recovery; no alarm, medicine or patient data. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class RestoredUiBooleanTest {
    @Test
    public void wrongTypeUISettingsCannotCrashHomeAgendaOrMedicineSearch() {
        for (PreferenceKeys key : new PreferenceKeys[]{
                PreferenceKeys.DRUGDB_DB_PROMPT,
                PreferenceKeys.HOME_INTRO_SHOWN,
                PreferenceKeys.HOME_DAILYAGENDA_EXPANDED,
                PreferenceKeys.MEDICINES_USE_PRESCRIPTIONS_SHOWN}) {
            try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
                prefs.when(() -> PreferenceUtils.getBoolean(key, false))
                        .thenThrow(new ClassCastException("Synthetic incompatible legacy value"));
                assertFalse(RestoredUiBoolean.read(key, false));
            }
        }
    }

    @Test
    public void properlyStoredSelectionsAndDefaultsAreNotOverwritten() {
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getBoolean(
                    PreferenceKeys.HOME_INTRO_SHOWN, false)).thenReturn(true);
            assertTrue(RestoredUiBoolean.read(PreferenceKeys.HOME_INTRO_SHOWN, false));
        }
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getBoolean(
                    PreferenceKeys.HOME_DAILYAGENDA_EXPANDED, false)).thenReturn(false);
            assertFalse(RestoredUiBoolean.read(PreferenceKeys.HOME_DAILYAGENDA_EXPANDED, false));
        }
    }

    @Test
    public void fallbackPolicyRemainsExplicitForDifferentUIs() {
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getBoolean(
                    PreferenceKeys.HOME_INTRO_SHOWN, true))
                    .thenThrow(new ClassCastException("Synthetic invalid Boolean"));
            assertTrue(RestoredUiBoolean.read(PreferenceKeys.HOME_INTRO_SHOWN, true));
        }
    }
}
