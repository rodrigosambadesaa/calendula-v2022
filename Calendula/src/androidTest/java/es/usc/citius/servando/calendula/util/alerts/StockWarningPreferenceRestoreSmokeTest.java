/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util.alerts;

import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Runs only on disposable CI emulator profiles. Restores the original key
 * after testing actual platform SharedPreferences wrong-type exceptions.
 */
@RunWith(AndroidJUnit4.class)
public class StockWarningPreferenceRestoreSmokeTest {
    @Test
    public void wrongTypeSavedThresholdUsesSafeWarningAndPreservesExplicitOptOut() {
        final SharedPreferences preferences = PreferenceUtils.instance().preferences();
        final String key = PreferenceKeys.SETTINGS_STOCK_ALERT_DAYS.key();
        final boolean hadValue = preferences.contains(key);
        final Object oldValue = preferences.getAll().get(key);
        try {
            assertTrue(preferences.edit().putInt(key, 365).commit());
            assertEquals(7, StockAlertHandler.configuredStockWarningDays());

            assertTrue(preferences.edit().putBoolean(key, false).commit());
            assertEquals(7, StockAlertHandler.configuredStockWarningDays());

            assertTrue(preferences.edit().putString(key, "-1").commit());
            assertEquals(-1, StockAlertHandler.configuredStockWarningDays());

            assertTrue(preferences.edit().putString(key, "21").commit());
            assertEquals(21, StockAlertHandler.configuredStockWarningDays());
        } finally {
            SharedPreferences.Editor restore = preferences.edit();
            if (!hadValue || oldValue == null) {
                restore.remove(key);
            } else if (oldValue instanceof String) {
                restore.putString(key, (String) oldValue);
            } else if (oldValue instanceof Integer) {
                restore.putInt(key, (Integer) oldValue);
            } else if (oldValue instanceof Boolean) {
                restore.putBoolean(key, (Boolean) oldValue);
            } else if (oldValue instanceof Long) {
                restore.putLong(key, (Long) oldValue);
            } else if (oldValue instanceof Float) {
                restore.putFloat(key, (Float) oldValue);
            } else {
                // No test code should manufacture a new value of an unknown
                // type during cleanup; the original app is a fresh fixture.
                throw new AssertionError("Unexpected original threshold type");
            }
            assertTrue("CI must restore the original preference", restore.commit());
        }
    }
}
