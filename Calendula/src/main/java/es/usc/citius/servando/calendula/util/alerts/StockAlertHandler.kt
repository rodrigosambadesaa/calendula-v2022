/*
 *    Calendula - An assistant for personal medication management.
 *    Copyright (C) 2016 CITIUS - USC
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

package es.usc.citius.servando.calendula.util.alerts

import es.usc.citius.servando.calendula.CalendulaApp
import es.usc.citius.servando.calendula.database.DB
import es.usc.citius.servando.calendula.events.StockRunningOutEvent
import es.usc.citius.servando.calendula.persistence.Medicine
import es.usc.citius.servando.calendula.persistence.alerts.StockRunningOutAlert
import es.usc.citius.servando.calendula.util.PreferenceKeys
import es.usc.citius.servando.calendula.util.PreferenceUtils
import es.usc.citius.servando.calendula.util.stock.MedicineScheduleStockProvider
import es.usc.citius.servando.calendula.util.stock.StockCalculator
import org.joda.time.LocalDate


object StockAlertHandler {

    /**
     * The notification preference exposes only these four values. A missing
     * or corrupt legacy preference must not crash a medication save, nor
     * silently disable useful low-stock warnings.
     *
     * -1 explicitly means "do not warn"; 7 matches the preference XML default.
     */
    @JvmStatic
    fun stockWarningDays(raw: String?): Int = when (raw?.trim()) {
        "-1" -> -1
        "7" -> 7
        "14" -> 14
        "21" -> 21
        else -> 7
    }

    /**
     * Do not calculate or clear clinical stock alerts from malformed values.
     * A negative quantity (other than the distinct -1 disabled sentinel),
     * NaN or infinity cannot produce a meaningful stock depletion date.
     */
    @JvmStatic
    fun isUsableStockValue(stock: Float?): Boolean =
        stock != null && stock >= 0f && !stock.isNaN() && !stock.isInfinite()

    @JvmStatic
    fun checkStockAlerts(m: Medicine) {
        if (!m.stockManagementEnabled()) {
            return
        }
        val currentStock = m.stock ?: return
        if (!isUsableStockValue(currentStock)) {
            // Leave existing alerts intact; deleting one based on an invalid
            // stock estimate could wrongly signal that supply is adequate.
            return
        }

        val stockAlertPref = stockWarningDays(
            PreferenceUtils.getString(PreferenceKeys.SETTINGS_STOCK_ALERT_DAYS, "7")
        )
        val alerts =
            DB.alerts().findByMedicineAndType(m, StockRunningOutAlert::class.java.canonicalName)

        if (stockAlertPref == -1) {
            // An explicit disable request revokes obsolete low-stock alerts,
            // but must never alter independent HIGH medication safety alerts.
            for (alert in alerts) {
                AlertManager.removeAlert(alert)
            }
            return
        }

        val stockEnd = StockCalculator.calculateStockEnd(
            LocalDate.now(),
            MedicineScheduleStockProvider(m),
            currentStock
        )

        if (stockEnd is StockCalculator.StockEnd.OnDate && stockEnd.days < stockAlertPref && alerts.isEmpty()) {
            AlertManager.createAlert(StockRunningOutAlert(m, LocalDate.now()))
            CalendulaApp.eventBus().post(StockRunningOutEvent(m, stockEnd.days))
        } else if (alerts.isNotEmpty() &&
            ((stockEnd is StockCalculator.StockEnd.OnDate && stockEnd.days >= stockAlertPref)
                || stockEnd == StockCalculator.StockEnd.OverMax)) {
            for (alert in alerts) {
                AlertManager.removeAlert(alert)
            }
        }
    }
}
