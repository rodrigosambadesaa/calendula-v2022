/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.drugdb;

import android.content.SharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DrugCatalogPreferencePolicyTest {
    private static final String KEY = "medicine_catalog";

    @Test
    public void incompatibleOrNullRestoredIdCannotSelectUnknownCatalog() {
        SharedPreferences prefs = mock(SharedPreferences.class);
        when(prefs.getString(KEY, "none"))
                .thenThrow(new ClassCastException("Synthetic restored Boolean"));
        assertEquals("none", DrugCatalogPreferencePolicy.selectedIdOrFallback(
                prefs, KEY, "none"));
        assertEquals(null, DrugCatalogPreferencePolicy.selectedIdOrFallback(
                prefs, KEY, null));
    }

    @Test
    public void existingCatalogSelectionIsPreserved() {
        SharedPreferences prefs = mock(SharedPreferences.class);
        when(prefs.getString(KEY, "none")).thenReturn("aemps");
        assertEquals("aemps", DrugCatalogPreferencePolicy.selectedIdOrFallback(
                prefs, KEY, "none"));
    }

    @Test
    public void discontinuedProviderFallsBackToRegisteredDefault() {
        Object fallback = new Object();
        Object installed = new Object();
        Map<String, Object> providers = new HashMap<>();
        providers.put("aemps", installed);
        assertSame(fallback, DrugCatalogPreferencePolicy.knownCatalogOrDefault(
                providers, "old-removed-catalog", fallback));
        assertSame(fallback, DrugCatalogPreferencePolicy.knownCatalogOrDefault(
                providers, null, fallback));
        assertSame(installed, DrugCatalogPreferencePolicy.knownCatalogOrDefault(
                providers, "aemps", fallback));
    }

    @Test
    public void uninitializedProviderEntryAlsoFallsBack() {
        Object fallback = new Object();
        Map<String, Object> providers = new HashMap<>();
        providers.put("partially-loaded", null);
        assertSame(fallback, DrugCatalogPreferencePolicy.knownCatalogOrDefault(
                providers, "partially-loaded", fallback));
    }
}
