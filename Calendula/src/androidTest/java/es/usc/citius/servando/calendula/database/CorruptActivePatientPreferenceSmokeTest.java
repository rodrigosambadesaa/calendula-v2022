/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.database;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Disposable CI app only. No patients are created, selected or modified. */
@RunWith(AndroidJUnit4.class)
public class CorruptActivePatientPreferenceSmokeTest {
    @Test
    public void incompatibleSavedPatientCannotAuthorizeOrSelectAnotherProfile() {
        assertTrue(DB.initialized);
        final Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final SharedPreferences settings = PreferenceUtils.instance().preferences();
        final String key = PreferenceKeys.PATIENTS_ACTIVE.key();
        final boolean hadValue = settings.contains(key);
        final Object previous = settings.getAll().get(key);
        Patient synthetic = new Patient();
        synthetic.setId(999991L);
        try {
            assertTrue(settings.edit().putString(key, "bad patient ID").commit());
            assertNull("Never show the default patient's clinical records",
                    DB.patients().getActive(ctx));
            assertFalse(DB.patients().isActive(synthetic, ctx));

            assertTrue(settings.edit().putBoolean(key, true).commit());
            assertNull(DB.patients().getActive(ctx));
            assertFalse(DB.patients().isActive(synthetic, ctx));
        } finally {
            SharedPreferences.Editor restore = settings.edit();
            if (!hadValue || previous == null) {
                restore.remove(key);
            } else if (previous instanceof Long) {
                restore.putLong(key, (Long) previous);
            } else if (previous instanceof String) {
                restore.putString(key, (String) previous);
            } else if (previous instanceof Boolean) {
                restore.putBoolean(key, (Boolean) previous);
            } else if (previous instanceof Integer) {
                restore.putInt(key, (Integer) previous);
            } else if (previous instanceof Float) {
                restore.putFloat(key, (Float) previous);
            } else {
                throw new AssertionError("Unknown original patient preference type");
            }
            assertTrue("Selected patient preference must be restored", restore.commit());
        }
    }
}
