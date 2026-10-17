/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.database.migrationHelpers;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Collections;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * All-platform pure-policy smoke: no patient, prescription, SQLite or catalog
 * update is touched.
 */
@RunWith(AndroidJUnit4.class)
public class MedicineRelinkCatalogSelectionSmokeTest {
    @Test public void onlyExistingSelectedProviderCanRelinkMedications() {
        assertNull(MedicineRelinkCatalogPolicy.registeredIdOrNull(null,
                Collections.singletonList("aemps")));
        assertNull(MedicineRelinkCatalogPolicy.registeredIdOrNull("none",
                Collections.singletonList("aemps")));
        assertNull(MedicineRelinkCatalogPolicy.registeredIdOrNull("obsolete",
                Collections.singletonList("aemps")));
        assertEquals("aemps", MedicineRelinkCatalogPolicy.registeredIdOrNull("aemps",
                Collections.singletonList("aemps")));
    }
}
