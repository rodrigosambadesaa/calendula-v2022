/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.database.migrationHelpers;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Catalog-selection tests never access a patient's medicine database. */
public class MedicineRelinkCatalogPolicyTest {
    @Test public void validRegisteredCatalogIsPreservedWithoutSelectingDefault() {
        assertEquals("aemps", MedicineRelinkCatalogPolicy.registeredIdOrNull(
                "aemps", Collections.singletonList("aemps")));
    }

    @Test public void noneInstallingRemovedOrMalformedSelectionsDoNotRelinkMedicine() {
        for (String invalid : new String[]{null, "", " ", "none", "setting_up", "removed_catalog"}) {
            assertNull(invalid, MedicineRelinkCatalogPolicy.registeredIdOrNull(
                    invalid, Collections.singletonList("aemps")));
        }
        assertNull(MedicineRelinkCatalogPolicy.registeredIdOrNull("aemps", null));
        assertNull(MedicineRelinkCatalogPolicy.registeredIdOrNull("aemps",
                Collections.<String>emptyList()));
        assertNull(MedicineRelinkCatalogPolicy.registeredIdOrNull("aemps",
                Arrays.asList("other_catalog", "another_catalog")));
    }

    @Test public void noCaseInsensitiveOrWhitespaceAliasCanRewriteMedications() {
        assertNull(MedicineRelinkCatalogPolicy.registeredIdOrNull(
                "AEMPS", Collections.singletonList("aemps")));
        assertNull(MedicineRelinkCatalogPolicy.registeredIdOrNull(
                " aemps", Collections.singletonList("aemps")));
    }
}
