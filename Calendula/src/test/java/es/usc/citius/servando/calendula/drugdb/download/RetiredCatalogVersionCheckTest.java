/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.drugdb.download;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.util.PreferenceUtils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mockStatic;

/**
 * Retired unsigned SQL database archives may never read potentially corrupt
 * saved selection/version values or perform network checks.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class RetiredCatalogVersionCheckTest {
    @Test
    public void noLegacyBackendMeansNoUpdateOrRestoredPreferencesRead() {
        assertFalse(LegacyRemoteArchivePolicy.permitsRemoteSqlInstallation());
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            Context context = ApplicationProvider.getApplicationContext();
            assertNull(DBVersionManager.checkForUpdate(context));
            // Even an absent Context must never attempt to inspect settings.
            assertNull(DBVersionManager.checkForUpdate(null));
            prefs.verifyNoInteractions();
        }
    }
}
