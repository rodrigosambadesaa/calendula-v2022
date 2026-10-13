/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util.alerts

import org.junit.Assert.assertEquals
import org.junit.Test

/** No database, medicine, Android alarms or patient information is involved. */
class StockAlertPreferenceTest {

    @Test
    fun acceptedListPreferenceValuesMatchTheStockSettingsScreen() {
        assertEquals(-1, StockAlertHandler.parseStockAlertDays("-1"))
        assertEquals(7, StockAlertHandler.parseStockAlertDays("7"))
        assertEquals(14, StockAlertHandler.parseStockAlertDays("14"))
        assertEquals(21, StockAlertHandler.parseStockAlertDays("21"))
        assertEquals(21, StockAlertHandler.parseStockAlertDays(" 21 "))
    }

    @Test
    fun malformedOrLegacyValuesSafelyUseTheSevenDayUiDefault() {
        for (raw in listOf<String?>(
                null, "", " ", "abc", "1e9", "2147483648",
                "-2147483649", "-999", "0", "1", "365", "999999999")) {
            assertEquals("Invalid stock threshold: $raw",
                    7, StockAlertHandler.parseStockAlertDays(raw))
        }
    }
}
