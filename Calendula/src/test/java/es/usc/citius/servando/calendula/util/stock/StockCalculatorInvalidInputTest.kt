/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util.stock

import org.joda.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure regression coverage; never reads a patient's actual medication data. */
class StockCalculatorInvalidInputTest {

    private val today = LocalDate(2026, 10, 13)

    @Test
    fun invalidInitialStockNeverReportsMoreThanThreeMonths() {
        val invalid = floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY, -0.5f, -1f)
        invalid.forEach { stock ->
            var providerCalls = 0
            val provider = object : StockForDayProvider {
                override fun stockNeededForDay(date: LocalDate): Float {
                    providerCalls++
                    return 1f
                }
            }
            assertEquals(
                StockCalculator.StockEnd.Unavailable,
                StockCalculator.calculateStockEnd(today, provider, stock)
            )
            assertEquals("Invalid stock must not even query medication schedules",
                0, providerCalls)
        }
    }

    @Test
    fun invalidScheduledConsumptionNeverReportsAmpleStock() {
        val invalid = floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY, -2f)
        invalid.forEach { dailyNeed ->
            val provider = object : StockForDayProvider {
                override fun stockNeededForDay(date: LocalDate): Float = dailyNeed
            }
            assertEquals(
                StockCalculator.StockEnd.Unavailable,
                StockCalculator.calculateStockEnd(today, provider, 10f)
            )
        }
    }

    @Test
    fun invalidPersistedRecurrenceNeverCrashesStockOrClaimsAmpleSupply() {
        var queriedDays = 0
        val corruptRecurrence = object : StockForDayProvider {
            override fun stockNeededForDay(date: LocalDate): Float {
                queriedDays++
                if (queriedDays == 2) {
                    throw IllegalStateException("Synthetic corrupt patient recurrence")
                }
                return 0.25f
            }
        }
        assertEquals(
            StockCalculator.StockEnd.Unavailable,
            StockCalculator.calculateStockEnd(today, corruptRecurrence, 100f)
        )
        assertEquals("The calculation must stop at the first invalid dose day",
            2, queriedDays)
    }

    @Test
    fun validZeroConsumptionStillPreservesExistingOverMaxSemantics() {
        val noConsumption = object : StockForDayProvider {
            override fun stockNeededForDay(date: LocalDate): Float = 0f
        }
        assertEquals(StockCalculator.StockEnd.OverMax,
            StockCalculator.calculateStockEnd(today, noConsumption, 10f))
    }

    @Test
    fun validDepletionRetainsItsExactDate() {
        val onePerDay = object : StockForDayProvider {
            override fun stockNeededForDay(date: LocalDate): Float = 1f
        }
        val estimate = StockCalculator.calculateStockEnd(today, onePerDay, 2f)
        assertTrue(estimate is StockCalculator.StockEnd.OnDate)
        assertEquals(today.plusDays(2), (estimate as StockCalculator.StockEnd.OnDate).date)
    }
}
