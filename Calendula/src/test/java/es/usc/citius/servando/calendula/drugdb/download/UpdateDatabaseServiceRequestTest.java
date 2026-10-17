/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.drugdb.download;

import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** This test never starts the service, accesses the DB or downloads a catalog. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class UpdateDatabaseServiceRequestTest {
    @Test public void nullAndEmptyLegacyRequestsAreIgnored() {
        assertNull(UpdateDatabaseService.requestedCatalogOrNull(null));
        assertNull(UpdateDatabaseService.requestedCatalogOrNull(new Intent()));
        assertNull(UpdateDatabaseService.requestedCatalogOrNull(new Intent()
                .putExtra(UpdateDatabaseService.EXTRA_DATABASE_ID, " ")));
    }

    @Test public void wrongTypeExtraNeverStartsAnUnrelatedCatalogUpdate() {
        Intent malformed = new Intent()
                .putExtra(UpdateDatabaseService.EXTRA_DATABASE_ID, 29);
        assertNull(UpdateDatabaseService.requestedCatalogOrNull(malformed));
        assertFalse(UpdateDatabaseService.isSameSelectedCatalog(null, "aemps"));
    }

    @Test public void onlyExactPersistedCatalogIdMatchesRequestedCatalog() {
        Intent valid = new Intent()
                .putExtra(UpdateDatabaseService.EXTRA_DATABASE_ID, "aemps");
        assertEquals("aemps", UpdateDatabaseService.requestedCatalogOrNull(valid));
        assertTrue(UpdateDatabaseService.isSameSelectedCatalog("aemps", "aemps"));
        assertFalse(UpdateDatabaseService.isSameSelectedCatalog("aemps", "other"));
        assertFalse(UpdateDatabaseService.isSameSelectedCatalog("aemps", null));
        assertFalse(UpdateDatabaseService.isSameSelectedCatalog("", "aemps"));
    }
}
