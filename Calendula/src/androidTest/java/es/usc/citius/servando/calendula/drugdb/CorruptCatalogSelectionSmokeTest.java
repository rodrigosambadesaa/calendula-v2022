/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.drugdb;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** CI emulator only; no catalog files, patient records or SQL data are changed. */
@RunWith(AndroidJUnit4.class)
public class CorruptCatalogSelectionSmokeTest {
    @Test
    public void actualAndroidWrongTypeAndRemovedProviderUseRegisteredDefault() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences settings = PreferenceUtils.instance().preferences();
        String key = PreferenceKeys.DRUGDB_CURRENT_DB.key();
        boolean wasPresent = settings.contains(key);
        Object previous = settings.getAll().get(key);
        DBRegistry registry = DBRegistry.init(context);
        try {
            assertTrue(settings.edit().putInt(key, 1234).commit());
            assertSame(registry.defaultDBMgr(), registry.current());
            assertTrue(settings.edit().putBoolean(key, true).commit());
            assertSame(registry.defaultDBMgr(), registry.current());
            assertTrue(settings.edit().putString(key, "removed-catalog").commit());
            assertSame(registry.defaultDBMgr(), registry.current());
            assertTrue(settings.edit().putString(key, context.getString(
                    R.string.database_aemps_id)).commit());
            assertSame(registry.defaultDBMgr(), registry.current());
        } finally {
            SharedPreferences.Editor restore = settings.edit();
            if (!wasPresent || previous == null) {
                restore.remove(key);
            } else if (previous instanceof String) {
                restore.putString(key, (String) previous);
            } else if (previous instanceof Integer) {
                restore.putInt(key, (Integer) previous);
            } else if (previous instanceof Boolean) {
                restore.putBoolean(key, (Boolean) previous);
            } else if (previous instanceof Long) {
                restore.putLong(key, (Long) previous);
            } else if (previous instanceof Float) {
                restore.putFloat(key, (Float) previous);
            } else {
                throw new AssertionError("Unrecognized original catalog preference type");
            }
            assertTrue("CI must restore previous selection", restore.commit());
        }
    }
}
