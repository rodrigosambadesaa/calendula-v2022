/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util.stock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure checks: no UI, alarms, or patient database records. */
class StockWarningPresentationGuardTest {
    @Test
    fun currentMedicationMustBelongToTheCurrentPatient() {
        assertTrue(StockDisplayUtils.ownerMatchesActivePatient(21L, 21L, 21L))
        assertFalse(StockDisplayUtils.ownerMatchesActivePatient(21L, 22L, 21L))
        assertFalse(StockDisplayUtils.ownerMatchesActivePatient(21L, 21L, 22L))
        assertFalse(StockDisplayUtils.ownerMatchesActivePatient(null, 21L, 21L))
        assertFalse(StockDisplayUtils.ownerMatchesActivePatient(21L, null, 21L))
        assertFalse(StockDisplayUtils.ownerMatchesActivePatient(21L, 21L, null))
        assertFalse(StockDisplayUtils.ownerMatchesActivePatient(0L, 0L, 0L))
    }

    @Test
    fun fractionalStockCannotBeSilentlyRenderedAsZero() {
        assertEquals("0.5", StockDisplayUtils.displayableQuantity(0.5f))
        assertEquals("1.25", StockDisplayUtils.displayableQuantity(1.25f))
        assertEquals("0", StockDisplayUtils.displayableQuantity(0f))
        assertEquals("2", StockDisplayUtils.displayableQuantity(2f))
    }

    @Test
    fun invalidOrDisabledInventorySuppressesMisleadingWarning() {
        assertNull(StockDisplayUtils.displayableQuantity(null))
        assertNull(StockDisplayUtils.displayableQuantity(-1f))
        assertNull(StockDisplayUtils.displayableQuantity(Float.NaN))
        assertNull(StockDisplayUtils.displayableQuantity(Float.POSITIVE_INFINITY))
        assertNull(StockDisplayUtils.displayableQuantity(Float.NEGATIVE_INFINITY))
    }
}
