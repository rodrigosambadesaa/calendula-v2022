/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util.alerts;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mockStatic;

/** Synthetic restored threshold, no medicine, stock or alert database writes. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class StockWarningPreferenceRestoreTest {
    @Test
    public void wrongTypeThresholdDoesNotDropLowStockWarnings() {
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getString(
                    PreferenceKeys.SETTINGS_STOCK_ALERT_DAYS, "7"))
                    .thenThrow(new ClassCastException("Synthetic restored integer"));
            assertEquals(7, StockAlertHandler.configuredStockWarningDays());
        }
    }

    @Test
    public void explicitOptOutStillWins() {
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getString(
                    PreferenceKeys.SETTINGS_STOCK_ALERT_DAYS, "7")).thenReturn("-1");
            assertEquals(-1, StockAlertHandler.configuredStockWarningDays());
        }
    }

    @Test
    public void permittedCustomThresholdIsPreserved() {
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getString(
                    PreferenceKeys.SETTINGS_STOCK_ALERT_DAYS, "7")).thenReturn("21");
            assertEquals(21, StockAlertHandler.configuredStockWarningDays());
        }
    }

    @Test
    public void invalidStringUsesSafeDocumentedDefault() {
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getString(
                    PreferenceKeys.SETTINGS_STOCK_ALERT_DAYS, "7")).thenReturn("9999");
            assertEquals(7, StockAlertHandler.configuredStockWarningDays());
        }
    }
}
