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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mockStatic;

/** Pure preferences fault injection; no clinical database or network calls. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DrugCatalogSelectionTest {
    private static final String CATALOG = "aemps";
    private static final String NONE = "none";
    private static final String SETTING_UP = "setting_up";

    @Test
    public void incompatibleRestoredCatalogNeverEnablesCatalogBackedOperations() {
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getString(
                    PreferenceKeys.DRUGDB_CURRENT_DB, null))
                    .thenThrow(new ClassCastException("synthetic restored integer"));
            assertNull(DrugCatalogSelection.selectedOrNull());
            assertFalse(DrugCatalogSelection.isSelected(CATALOG));
            assertFalse(DrugCatalogSelection.isReady(NONE, SETTING_UP));
        }
    }

    @Test
    public void missingOrBlankCatalogCannotBeInterpretedAsInstalled() {
        for (String invalid : new String[]{null, "", "  "}) {
            try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
                prefs.when(() -> PreferenceUtils.getString(
                        PreferenceKeys.DRUGDB_CURRENT_DB, null)).thenReturn(invalid);
                assertFalse(DrugCatalogSelection.isReady(NONE, SETTING_UP));
                assertFalse(DrugCatalogSelection.isSelected(CATALOG));
            }
        }
    }

    @Test
    public void disabledAndInstallingCatalogsDoNotEnableBarcodeLookup() {
        for (String disabled : new String[]{NONE, SETTING_UP}) {
            try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
                prefs.when(() -> PreferenceUtils.getString(
                        PreferenceKeys.DRUGDB_CURRENT_DB, null)).thenReturn(disabled);
                assertFalse(DrugCatalogSelection.isReady(NONE, SETTING_UP));
                assertFalse(DrugCatalogSelection.isSelected(CATALOG));
            }
        }
    }

    @Test
    public void installedCatalogMatchesOnlyItsOriginalIdentifier() {
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getString(
                    PreferenceKeys.DRUGDB_CURRENT_DB, null)).thenReturn(CATALOG);
            assertTrue(DrugCatalogSelection.isSelected(CATALOG));
            assertFalse(DrugCatalogSelection.isSelected("different_catalog"));
            assertFalse(DrugCatalogSelection.isSelected(null));
            assertTrue(DrugCatalogSelection.isReady(NONE, SETTING_UP));
        }
    }
}
