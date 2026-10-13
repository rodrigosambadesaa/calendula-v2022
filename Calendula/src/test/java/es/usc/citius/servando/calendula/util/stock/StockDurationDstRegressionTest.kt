/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util.stock

import org.joda.time.DateTimeZone
import org.joda.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic, calendar-only stock estimates; no medications are persisted. */
class StockDurationDstRegressionTest {

    private val oneUnitPerDay = object : StockForDayProvider {
        override fun stockNeededForDay(date: LocalDate): Float = 1f
    }

    @Test
    fun marchSpringForwardStillCountsOneCalendarDayOfMedicineStock() {
        withMadridTimeZone {
            // In Madrid, March 29 -> March 30, 2026 takes only 23 hours.
            // Duration.standardDays previously returned 0 for a full
            // calendar day, triggering premature low-stock warnings.
            val start = LocalDate(2026, 3, 29)
            val result = StockCalculator.calculateStockEnd(start, oneUnitPerDay, 1f)
            assertTrue(result is StockCalculator.StockEnd.OnDate)
            val exhausted = result as StockCalculator.StockEnd.OnDate
            assertEquals(start.plusDays(1), exhausted.date)
            assertEquals(1L, exhausted.days)
        }
    }

    @Test
    fun marchSpringForwardDoesNotShortenTwoDayEstimate() {
        withMadridTimeZone {
            val start = LocalDate(2026, 3, 28)
            val result = StockCalculator.calculateStockEnd(start, oneUnitPerDay, 2f)
            assertTrue(result is StockCalculator.StockEnd.OnDate)
            val exhausted = result as StockCalculator.StockEnd.OnDate
            assertEquals(start.plusDays(2), exhausted.date)
            assertEquals(2L, exhausted.days)
        }
    }

    @Test
    fun octoberAutumnClockChangeAlsoCountsCalendarDays() {
        withMadridTimeZone {
            val start = LocalDate(2026, 10, 25)
            val result = StockCalculator.calculateStockEnd(start, oneUnitPerDay, 1f)
            assertTrue(result is StockCalculator.StockEnd.OnDate)
            val exhausted = result as StockCalculator.StockEnd.OnDate
            assertEquals(start.plusDays(1), exhausted.date)
            assertEquals(1L, exhausted.days)
        }
    }

    private inline fun withMadridTimeZone(block: () -> Unit) {
        val previous = DateTimeZone.getDefault()
        try {
            DateTimeZone.setDefault(DateTimeZone.forID("Europe/Madrid"))
            block()
        } finally {
            DateTimeZone.setDefault(previous)
        }
    }
}
