/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.drugdb.download;

import android.content.Intent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Framework-only parsing: do not start an installer or touch patient records. */
@RunWith(AndroidJUnit4.class)
public class CatalogUpdateRequestSmokeTest {
    @Test public void malformedLegacyIntentsAreFailClosedOnRealAndroid() {
        assertNull(UpdateDatabaseService.requestedCatalogOrNull(null));
        assertNull(UpdateDatabaseService.requestedCatalogOrNull(new Intent()));
        assertNull(UpdateDatabaseService.requestedCatalogOrNull(new Intent()
                .putExtra(UpdateDatabaseService.EXTRA_DATABASE_ID, true)));
        assertNull(UpdateDatabaseService.requestedCatalogOrNull(new Intent()
                .putExtra(UpdateDatabaseService.EXTRA_DATABASE_ID, "  ")));
        assertEquals("catalog-id", UpdateDatabaseService.requestedCatalogOrNull(
                new Intent().putExtra(UpdateDatabaseService.EXTRA_DATABASE_ID, "catalog-id")));
        assertTrue(UpdateDatabaseService.isSameSelectedCatalog("catalog-id", "catalog-id"));
        assertFalse(UpdateDatabaseService.isSameSelectedCatalog("catalog-id", "other-id"));
    }
}
