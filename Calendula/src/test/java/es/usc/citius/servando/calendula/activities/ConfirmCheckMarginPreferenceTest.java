/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.core.util.Pair;
import androidx.test.core.app.ApplicationProvider;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Isolated preference and interval tests, with no patient or medication records. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ConfirmCheckMarginPreferenceTest {

    @Test
    public void numericBoundsAndMalformedValuesNeverExpandTheApprovalWindow() {
        assertEquals(3, ConfirmActivity.safeCheckMarginHours(null));
        assertEquals(3, ConfirmActivity.safeCheckMarginHours(""));
        assertEquals(3, ConfirmActivity.safeCheckMarginHours("not a number"));
        assertEquals(3, ConfirmActivity.safeCheckMarginHours("-1"));
        assertEquals(3, ConfirmActivity.safeCheckMarginHours("25"));
        assertEquals(3, ConfirmActivity.safeCheckMarginHours("2147483647"));
        assertEquals(3, ConfirmActivity.safeCheckMarginHours("999999999999999999"));
        assertEquals(0, ConfirmActivity.safeCheckMarginHours("0"));
        assertEquals(2, ConfirmActivity.safeCheckMarginHours(" 2 "));
        assertEquals(24, ConfirmActivity.safeCheckMarginHours("24"));
    }

    @Test
    public void legacyIncorrectPreferenceTypeCannotCrashConfirmation() {
        Context context = ApplicationProvider.getApplicationContext();
        PreferenceUtils.init(context);
        SharedPreferences prefs = PreferenceUtils.instance().preferences();
        String key = PreferenceKeys.CONFIRM_CHECK_WINDOW_MARGIN.key();
        boolean existed = prefs.contains(key);
        Object previous = prefs.getAll().get(key);
        DateTime doseTime = new DateTime(2026, 10, 12, 15, 0);
        try {
            assertTrue(prefs.edit().putString(key, "not-a-number").commit());
            Pair<DateTime, DateTime> malformed =
                    new ConfirmActivity().getCheckMarginInterval(doseTime);
            assertEquals(doseTime.minusMinutes(30), malformed.first);
            assertEquals(doseTime.plusHours(3), malformed.second);

            assertTrue(prefs.edit().putBoolean(key, true).commit());
            Pair<DateTime, DateTime> wrongType =
                    new ConfirmActivity().getCheckMarginInterval(doseTime);
            assertEquals(doseTime.minusMinutes(30), wrongType.first);
            assertEquals(doseTime.plusHours(3), wrongType.second);

            assertTrue(prefs.edit().putString(key, "2").commit());
            Pair<DateTime, DateTime> valid =
                    new ConfirmActivity().getCheckMarginInterval(doseTime);
            assertEquals(doseTime.minusMinutes(30), valid.first);
            assertEquals(doseTime.plusHours(2), valid.second);
        } finally {
            SharedPreferences.Editor restore = prefs.edit();
            if (!existed) {
                restore.remove(key);
            } else if (previous instanceof String) {
                restore.putString(key, (String) previous);
            } else if (previous instanceof Boolean) {
                restore.putBoolean(key, (Boolean) previous);
            } else if (previous instanceof Integer) {
                restore.putInt(key, (Integer) previous);
            } else if (previous instanceof Long) {
                restore.putLong(key, (Long) previous);
            } else if (previous instanceof Float) {
                restore.putFloat(key, (Float) previous);
            } else {
                restore.remove(key);
            }
            assertTrue(restore.commit());
        }
    }
}
