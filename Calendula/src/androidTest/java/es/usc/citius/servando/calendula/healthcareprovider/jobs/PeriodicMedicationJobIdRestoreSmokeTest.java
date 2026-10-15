/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.healthcareprovider.jobs;

import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** CI only: checks real Android preferences, never schedules a network job. */
@RunWith(AndroidJUnit4.class)
public class PeriodicMedicationJobIdRestoreSmokeTest {
    @Test
    public void restoredWrongTypeAndMissingJobIdDoNotCrashSyncGate() {
        final SharedPreferences prefs = PreferenceUtils.instance().preferences();
        final String key = PreferenceKeys.REMOTE_PERIODIC_JOB_ID.key();
        final boolean hadValue = prefs.contains(key);
        final Object previous = prefs.getAll().get(key);
        try {
            assertTrue(prefs.edit().putString(key, "legacy job").commit());
            assertEquals(-1, UpdateMedicationFromServiceJob.savedPeriodicJobIdOrMissing());
            assertTrue(prefs.edit().putBoolean(key, true).commit());
            assertEquals(-1, UpdateMedicationFromServiceJob.savedPeriodicJobIdOrMissing());
            assertTrue(prefs.edit().putInt(key, 13).commit());
            assertEquals(13, UpdateMedicationFromServiceJob.savedPeriodicJobIdOrMissing());
            assertTrue(prefs.edit().putInt(key, 0).commit());
            assertEquals(-1, UpdateMedicationFromServiceJob.savedPeriodicJobIdOrMissing());
        } finally {
            SharedPreferences.Editor restore = prefs.edit();
            if (!hadValue || previous == null) {
                restore.remove(key);
            } else if (previous instanceof Integer) {
                restore.putInt(key, (Integer) previous);
            } else if (previous instanceof String) {
                restore.putString(key, (String) previous);
            } else if (previous instanceof Boolean) {
                restore.putBoolean(key, (Boolean) previous);
            } else if (previous instanceof Long) {
                restore.putLong(key, (Long) previous);
            } else if (previous instanceof Float) {
                restore.putFloat(key, (Float) previous);
            } else {
                throw new AssertionError("Unknown saved job ID preference type");
            }
            assertTrue(restore.commit());
        }
    }
}
