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
import static org.mockito.Mockito.mockStatic;

/** Synthetic app language preferences; does not modify Android's global locale. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class LocaleHelperRestoreTest {

    @Test
    public void validLanguagesAndSystemFallbackRemainAvailable() {
        assertEquals("gl", LocaleHelper.supportedLanguageOrFallback("gl", "en"));
        assertEquals("es", LocaleHelper.supportedLanguageOrFallback("es", "en"));
        assertEquals("en", LocaleHelper.supportedLanguageOrFallback("en", "es"));
        assertEquals("pt", LocaleHelper.supportedLanguageOrFallback("pt", "en"));
        assertEquals("fr", LocaleHelper.supportedLanguageOrFallback(null, "fr"));
    }

    @Test
    public void brokenLanguageCodesCannotBecomeGlobalLocale() {
        for (String malformed : new String[]{
                "", " ", "EN", "en-US", "../../", "a", "abcd", "e1",
                "es\n", "es_ES", "javascript:alert(1)"}) {
            assertEquals("Malformed app language: " + malformed,
                    "es", LocaleHelper.supportedLanguageOrFallback(malformed, "es"));
        }
        assertEquals("en", LocaleHelper.supportedLanguageOrFallback(null, null));
        assertEquals("en", LocaleHelper.supportedLanguageOrFallback(" ", "??"));
    }

    @Test
    public void wrongTypeRestoredPreferenceDoesNotCrashActivityLocaleLookup() {
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getString(
                    PreferenceKeys.SETTINGS_PREFERRED_LANG, "es"))
                    .thenThrow(new ClassCastException("restored synthetic integer"));
            assertEquals("es", LocaleHelper.getLanguage(null, "es"));
        }
    }

    @Test
    public void correctlyTypedUserLocaleIsNotOverridden() {
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getString(
                    PreferenceKeys.SETTINGS_PREFERRED_LANG, "en")).thenReturn("gl");
            assertEquals("gl", LocaleHelper.getLanguage(null, "en"));
        }
    }
}
