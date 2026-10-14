/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util.stock

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure arithmetic tests: no SQLite or patient records. */
class StockUpdaterArithmeticTest {
    @Test
    fun fractionalCompletesAndUndoRestoreSameInventory() {
        val afterIntake = StockUpdater.checkedAdjustedStock(1.5f, 0.25, true)
        assertEquals(1.25f, afterIntake, 0f)
        assertEquals(1.5f, StockUpdater.checkedAdjustedStock(afterIntake, 0.25, false), 0f)
        assertEquals(0f, StockUpdater.checkedAdjustedStock(0.5f, 0.5, true), 0f)
        assertEquals(2.5f, StockUpdater.checkedAdjustedStock(2f, 0.5, false), 0f)
    }

    @Test
    fun malformedQuantitiesAndDosesCannotChangeStock() {
        reject { StockUpdater.checkedAdjustedStock(Float.NaN, 1.0, true) }
        reject { StockUpdater.checkedAdjustedStock(Float.POSITIVE_INFINITY, 1.0, true) }
        reject { StockUpdater.checkedAdjustedStock(-1f, 1.0, false) }
        reject { StockUpdater.checkedAdjustedStock(10f, Double.NaN, true) }
        reject { StockUpdater.checkedAdjustedStock(10f, Double.POSITIVE_INFINITY, true) }
        reject { StockUpdater.checkedAdjustedStock(10f, 0.0, true) }
        reject { StockUpdater.checkedAdjustedStock(10f, -1.0, true) }
        reject { StockUpdater.checkedAdjustedStock(0f, 0.5, true) }
        reject { StockUpdater.checkedAdjustedStock(Float.MAX_VALUE, 1e38, false) }
    }

    @Test
    fun floatRoundingCannotSilentlyDiscardACompletedOrRefundedDose() {
        // Float cannot represent a 0.001-unit difference at this magnitude.
        reject { StockUpdater.checkedAdjustedStock(1e30f, 0.001, true) }
        reject { StockUpdater.checkedAdjustedStock(1e30f, 0.001, false) }
        reject { StockUpdater.checkedAdjustedStock(Float.MIN_VALUE, Double.MIN_VALUE, false) }
        reject { StockUpdater.checkedAdjustedStock(Float.MIN_VALUE, Double.MIN_VALUE, true) }
    }

    private fun reject(block: () -> Unit) {
        try {
            block()
        } catch (expected: IllegalStateException) {
            return
        }
        throw AssertionError("Unrepresentable medication stock change must reject intake")
    }
}
