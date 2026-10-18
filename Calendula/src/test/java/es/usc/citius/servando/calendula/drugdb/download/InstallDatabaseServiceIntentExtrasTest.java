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

/** Malformed persisted service intents must fail closed before database work. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class InstallDatabaseServiceIntentExtrasTest {
    @Test
    public void validStringAndBooleanExtrasArePreserved() {
        Intent i = new Intent();
        i.putExtra("catalog", "cima");
        i.putExtra("silent", true);
        assertEquals("cima", InstallDatabaseService.safeStringExtra(i, "catalog"));
        assertTrue(InstallDatabaseService.safeBooleanExtra(i, "silent", false));
        assertFalse(InstallDatabaseService.safeBooleanExtra(new Intent(), "silent", false));
    }

    @Test
    public void wrongTypedOrAbsentCatalogExtrasCannotBeUsedAsPaths() {
        Intent i = new Intent();
        i.putExtra("catalog", 123456L);
        i.putExtra("silent", "true");
        assertNull(InstallDatabaseService.safeStringExtra(i, "catalog"));
        assertNull(InstallDatabaseService.safeStringExtra(i, "unknown"));
        assertNull(InstallDatabaseService.safeStringExtra(null, "catalog"));
        assertNull(InstallDatabaseService.safeStringExtra(i, null));
        assertFalse(InstallDatabaseService.safeBooleanExtra(i, "silent", false));
        assertTrue(InstallDatabaseService.safeBooleanExtra(i, "silent", true));
        assertTrue(InstallDatabaseService.safeBooleanExtra(null, "silent", true));
    }

    @Test
    public void incompleteOrCorruptCatalogRequestsCannotReachInstaller() {
        assertTrue(InstallDatabaseService.hasRequiredCatalogMetadata(
                false, "/staging/catalog.db", "catalog", "2026", null));
        assertFalse(InstallDatabaseService.hasRequiredCatalogMetadata(
                false, null, "catalog", "2026", null));
        assertFalse(InstallDatabaseService.hasRequiredCatalogMetadata(
                false, "/staging/catalog.db", null, "2026", null));
        assertFalse(InstallDatabaseService.hasRequiredCatalogMetadata(
                false, "/staging/catalog.db", "catalog", " ", null));
        assertTrue(InstallDatabaseService.hasRequiredCatalogMetadata(
                true, null, "catalog", null, "SETUP"));
        assertFalse(InstallDatabaseService.hasRequiredCatalogMetadata(
                true, null, "catalog", null, null));
        assertFalse(InstallDatabaseService.hasRequiredCatalogMetadata(
                true, null, " ", null, "UPDATE"));
    }

    @Test
    @Config(sdk = 33)
    public void typedCatalogExtrasRemainSafeOnAndroid13() {
        Intent i = new Intent();
        i.putExtra("catalog", 2);
        i.putExtra("silent", 9);
        assertNull(InstallDatabaseService.safeStringExtra(i, "catalog"));
        assertFalse(InstallDatabaseService.safeBooleanExtra(i, "silent", false));
        i.putExtra("catalog", "verified-database");
        i.putExtra("silent", true);
        assertEquals("verified-database", InstallDatabaseService.safeStringExtra(i, "catalog"));
        assertTrue(InstallDatabaseService.safeBooleanExtra(i, "silent", false));
    }
}
