/*
 *    Calendula - An assistant for personal medication management.
 *    Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 *    Calendula is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this software.  If not, see <http://www.gnu.org/licenses/>.
 */

package es.usc.citius.servando.calendula.util.stock

import java.math.BigDecimal
import org.joda.time.Days
import org.joda.time.LocalDate

object StockCalculator {

    const val MAX_STOCK_DURATION_MONTHS = 3


    /**
     * Calculates how long the stock in [currentStock] will last, starting from [startDate]
     * and with the stock consumption given by [stockProvider], up to a maximum of [MAX_STOCK_DURATION_MONTHS]
     *
     * @param startDate the start date for the calculation
     * @param stockProvider a [StockForDayProvider] that specifies how much stock would be consumed for a given date
     * @param currentStock the base quantity of stock
     * @return a [StockEnd.OnDate] when the result is calculable, a [StockEnd.OverMax] if it's ouside the simulation limits
     */
    @JvmStatic
    fun calculateStockEnd(
        startDate: LocalDate,
        stockProvider: StockForDayProvider,
        currentStock: Float
    ): StockEnd {
        // An invalid quantity must never be reported as "sufficient for more
        // than three months". This method is also used directly by UI code,
        // not only by StockAlertHandler.
        if (!currentStock.isFinite() || currentStock < 0f) {
            return StockEnd.Unavailable
        }
        val endDate = startDate.plusMonths(MAX_STOCK_DURATION_MONTHS)

        var virtualDate = LocalDate(startDate)
        // The dosage interface stores finite Floats, but repeated Float
        // subtraction can cross zero one day too early (e.g. 1.0 minus ten
        // daily doses of 0.1). Use their canonical decimal representation to
        // accumulate exact units across the 3-month simulation horizon.
        var virtualStock = BigDecimal(currentStock.toString())

        // simulate daily intakes until the stock runs out (or we reach the max duration)
        do {
            val stockForDay = try {
                stockProvider.stockNeededForDay(virtualDate)
            } catch (invalidClinicalSource: RuntimeException) {
                // An imported or deleted recurrence can fail while calculating
                // the next daily dose. Stock sufficiency must not be inferred
                // from incomplete clinical data, and the UI must not crash.
                return StockEnd.Unavailable
            }
            if (!stockForDay.isFinite() || stockForDay < 0f) {
                // Invalid recurrence/dosage data cannot be used to reassure a
                // patient that they have enough medication.
                return StockEnd.Unavailable
            }
            virtualStock = virtualStock.subtract(BigDecimal(stockForDay.toString()))

            if (virtualStock.signum() < 0) {
                return StockEnd.OnDate(
                    date = virtualDate,
                    // A calendar day may last 23 or 25 hours at a DST
                    // transition. Duration.standardDays truncates fixed 24h
                    // periods and understated medication supply in spring.
                    days = Days.daysBetween(startDate, virtualDate).days.toLong()
                )
            }

            virtualDate = virtualDate.plusDays(1)
        } while (virtualDate.isBefore(endDate))

        return StockEnd.OverMax
    }

    /**
     * Result of stock duration estimates
     */
    sealed class StockEnd {
        /** Invalid stock or consumption prevents a trustworthy estimate. */
        object Unavailable : StockEnd()

        /**
         * The estimation overstepped the maximum simulation time
         */
        object OverMax : StockEnd()

        /**
         * The stock ends on `date`
         * @param date the stock end date
         * @param days the duration of the stock in days
         */
        class OnDate(val date: LocalDate, val days: Long) : StockEnd()
    }
}