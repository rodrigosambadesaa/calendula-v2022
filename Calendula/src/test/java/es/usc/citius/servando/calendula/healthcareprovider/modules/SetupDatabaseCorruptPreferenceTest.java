/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.healthcareprovider.modules;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.BuildConfig;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SetupDatabaseCorruptPreferenceTest {
    private Context app;
    private SharedPreferences preferences;

    @Before
    public void before() {
        app = RuntimeEnvironment.getApplication();
        PreferenceUtils.init(app);
        preferences = PreferenceUtils.instance().preferences();
    }

    @After
    public void after() {
        preferences.edit()
                .remove(PreferenceKeys.DRUGDB_CURRENT_DB.key())
                .remove(PreferenceKeys.DRUGDB_VERSION.key())
                .commit();
    }

    @Test
    public void wrongTypeCatalogNameCannotCrashStartupOrForceReinstall() {
        preferences.edit()
                .putInt(PreferenceKeys.DRUGDB_CURRENT_DB.key(), 777)
                .putString(PreferenceKeys.DRUGDB_VERSION.key(), "20260101")
                .commit();
        assertNull(SetupDatabaseModule.checkForUpdate(app));
    }

    @Test
    public void wrongTypeCatalogVersionCannotCrashStartupOrForceReinstall() {
        preferences.edit()
                .putString(PreferenceKeys.DRUGDB_CURRENT_DB.key(), BuildConfig.BUNDLED_DB_NAME)
                .putBoolean(PreferenceKeys.DRUGDB_VERSION.key(), true)
                .commit();
        assertNull(SetupDatabaseModule.checkForUpdate(app));
    }
}
