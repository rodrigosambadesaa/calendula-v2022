/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util.alerts;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Pure input-policy checks; no DB records or patient stock are modified. */
public class StockAlertInputValidationTest {

    @Test
    public void everySupportedPreferenceRetainsItsMeaning() {
        assertEquals(-1, StockAlertHandler.stockWarningDays("-1"));
        assertEquals(7, StockAlertHandler.stockWarningDays("7"));
        assertEquals(14, StockAlertHandler.stockWarningDays("14"));
        assertEquals(21, StockAlertHandler.stockWarningDays("21"));
        assertEquals(14, StockAlertHandler.stockWarningDays(" 14 "));
    }

    @Test
    public void damagedOrMissingPreferenceFallsBackToSevenDays() {
        assertEquals(7, StockAlertHandler.stockWarningDays(null));
        assertEquals(7, StockAlertHandler.stockWarningDays(""));
        assertEquals(7, StockAlertHandler.stockWarningDays("unknown"));
        assertEquals(7, StockAlertHandler.stockWarningDays("-2"));
        assertEquals(7, StockAlertHandler.stockWarningDays("2147483648"));
        assertEquals(7, StockAlertHandler.stockWarningDays("0"));
        assertEquals(7, StockAlertHandler.stockWarningDays("30"));
    }

    @Test
    public void onlyFiniteNonNegativeStockCanDriveClinicalAlerts() {
        assertTrue(StockAlertHandler.isUsableStockValue(0.0f));
        assertTrue(StockAlertHandler.isUsableStockValue(0.5f));
        assertTrue(StockAlertHandler.isUsableStockValue(25f));
        assertFalse(StockAlertHandler.isUsableStockValue(null));
        assertFalse(StockAlertHandler.isUsableStockValue(-1f));
        assertFalse(StockAlertHandler.isUsableStockValue(-0.1f));
        assertFalse(StockAlertHandler.isUsableStockValue(Float.NaN));
        assertFalse(StockAlertHandler.isUsableStockValue(Float.POSITIVE_INFINITY));
        assertFalse(StockAlertHandler.isUsableStockValue(Float.NEGATIVE_INFINITY));
    }
}
