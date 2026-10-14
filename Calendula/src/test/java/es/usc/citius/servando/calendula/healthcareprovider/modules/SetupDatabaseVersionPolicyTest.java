/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.healthcareprovider.modules;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SetupDatabaseVersionPolicyTest {
    private static final String CATALOG = "aemps";
    private static final String PENDING = "Setting up";

    private boolean needsUpdate(String current, String oldVersion, String bundled, String newVersion) {
        return SetupDatabaseModule.shouldUpdateCatalog(
                current, oldVersion, bundled, newVersion, PENDING);
    }

    @Test
    public void absentAndMalformedOldCatalogPreferencesNeverCrashOrForceInstall() {
        assertFalse(needsUpdate(null, "20250101", CATALOG, "20260101"));
        assertFalse(needsUpdate(CATALOG, null, CATALOG, "20260101"));
        assertFalse(needsUpdate(CATALOG, "", CATALOG, "20260101"));
        assertFalse(needsUpdate(CATALOG, "not-a-date", CATALOG, "20260101"));
        assertFalse(needsUpdate(CATALOG, "20250101", CATALOG, "corrupt"));
        assertFalse(needsUpdate(CATALOG, "20250101", PENDING, "20260101"));
        assertFalse(needsUpdate("other-catalog", "20250101", CATALOG, "20260101"));
    }

    @Test
    public void validCatalogDateComparisonKeepsLegacyUpdateBehavior() {
        assertTrue(needsUpdate(CATALOG, "20250101", CATALOG, "20260101"));
        assertFalse(needsUpdate(CATALOG, "20260101", CATALOG, "20260101"));
        assertFalse(needsUpdate(CATALOG, "20270101", CATALOG, "20260101"));
    }
}
