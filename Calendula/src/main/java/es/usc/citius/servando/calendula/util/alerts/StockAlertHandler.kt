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

    /** Preserve the already-tested parser used by existing Kotlin callers. */
    internal fun parseStockAlertDays(raw: String?): Int = stockWarningDays(raw)

    /**
     * Do not calculate or clear clinical stock alerts from malformed values.
     * A negative quantity (other than the distinct -1 disabled sentinel),
     * NaN or infinity cannot produce a meaningful stock depletion date.
     */
    @JvmStatic
    fun isUsableStockValue(stock: Float?): Boolean =
        stock != null && stock >= 0f && !stock.isNaN() && !stock.isInfinite()

    /** Only the same live medicine and patient may drive stock alert changes. */
    @JvmStatic
    fun samePersistedMedicineOwner(
        requestedMedicineId: Long?, requestedPatientId: Long?,
        storedMedicineId: Long?, storedPatientId: Long?
    ): Boolean = requestedMedicineId != null && requestedMedicineId > 0L
        && requestedPatientId != null && requestedPatientId > 0L
        && requestedMedicineId == storedMedicineId
        && requestedPatientId == storedPatientId

    @JvmStatic
    fun checkStockAlerts(m: Medicine) {
        // Snapshot the expected identity and stock BEFORE the ORM refresh:
        // legacy DAO caches can return/mutate the original Java object.
        val requestedMedicineId = m.id
        val requestedPatientId = m.patient?.id
        val requestedStock = m.stock
        if (!samePersistedMedicineOwner(
                requestedMedicineId, requestedPatientId,
                requestedMedicineId, requestedPatientId
            )) {
            return
        }

        val stored = DB.medicines().findById(requestedMedicineId!!) ?: return
        if (DB.medicines().refresh(stored) != 1
            || !samePersistedMedicineOwner(
                requestedMedicineId, requestedPatientId, stored.id, stored.patient?.id
            ) || requestedStock != stored.stock) {
            return
        }

        val stockAlertPref = stockWarningDays(
            PreferenceUtils.getString(PreferenceKeys.SETTINGS_STOCK_ALERT_DAYS, "7")
        )
        // Query by persisted owner, not by a caller-supplied medicine with a
        // potentially forged patient. Never delete a cross-patient legacy row.
        val alerts = DB.alerts().findByMedicineAndType(
            stored, StockRunningOutAlert::class.java.canonicalName
        )
        if (alerts.any { it.patient?.id != requestedPatientId
                || it.medicine?.id != requestedMedicineId }) {
            return
        }

        if (stockAlertPref == -1 || !stored.stockManagementEnabled()) {
            // User explicitly disabled warnings or stock management.
            for (alert in alerts) {
                AlertManager.removeAlert(alert)
            }
            return
        }

        val currentStock = stored.stock ?: return
        if (!isUsableStockValue(currentStock)) {
            // Preserve the warning when the quantity cannot be trusted.
            return
        }

        val stockEnd = StockCalculator.calculateStockEnd(
            LocalDate.now(),
            MedicineScheduleStockProvider(stored),
            currentStock
        )
        // Revalidate after potentially expensive recurrence calculations:
        // never publish or remove an alert using an already changed snapshot.
        if (DB.medicines().refresh(stored) != 1
            || !samePersistedMedicineOwner(
                requestedMedicineId, requestedPatientId, stored.id, stored.patient?.id
            ) || currentStock != stored.stock) {
            return
        }

        if (stockEnd is StockCalculator.StockEnd.OnDate
            && stockEnd.days < stockAlertPref && alerts.isEmpty()) {
            AlertManager.createAlert(StockRunningOutAlert(stored, LocalDate.now()))
            CalendulaApp.eventBus().post(StockRunningOutEvent(stored, stockEnd.days))
        } else if (alerts.isNotEmpty()
            && ((stockEnd is StockCalculator.StockEnd.OnDate
                && stockEnd.days >= stockAlertPref)
                || stockEnd == StockCalculator.StockEnd.OverMax)) {
            for (alert in alerts) {
                AlertManager.removeAlert(alert)
            }
        }
    }
}
