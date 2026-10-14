/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util.stock

import org.joda.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Avoid phantom early low-stock alerts caused by accumulated Float error. */
class StockCalculatorDecimalPrecisionTest {
    private val start = LocalDate(2026, 10, 14)

    private fun dayWithDose(dailyDose: Float) = object : StockForDayProvider {
        override fun stockNeededForDay(date: LocalDate): Float = dailyDose
    }

    private fun endsAfter(initial: Float, daily: Float, days: Long) {
        val end = StockCalculator.calculateStockEnd(start, dayWithDose(daily), initial)
        assertTrue("A positive daily dose must eventually deplete this fixture", end is StockCalculator.StockEnd.OnDate)
        end as StockCalculator.StockEnd.OnDate
        assertEquals(days, end.days)
        assertEquals(start.plusDays(days.toInt()), end.date)
    }

    @Test
    fun decimalTenthsDoNotRunOutOneDayPrematurely() {
        endsAfter(1f, 0.1f, 10)
        endsAfter(0.3f, 0.1f, 3)
        endsAfter(1f, 0.2f, 5)
    }

    @Test
    fun mixedFractionalDosesKeepCalendarDayBoundary() {
        endsAfter(1.5f, 0.25f, 6)
        endsAfter(1.75f, 0.5f, 3)
        endsAfter(0.5f, 0.5f, 1)
    }

    @Test
    fun invalidDoseAndNoConsumptionKeepSafetySemantics() {
        assertEquals(StockCalculator.StockEnd.Unavailable,
                StockCalculator.calculateStockEnd(start, dayWithDose(Float.NaN), 1f))
        assertEquals(StockCalculator.StockEnd.Unavailable,
                StockCalculator.calculateStockEnd(start, dayWithDose(-0.1f), 1f))
        assertEquals(StockCalculator.StockEnd.OverMax,
                StockCalculator.calculateStockEnd(start, dayWithDose(0f), 1f))
    }
}
