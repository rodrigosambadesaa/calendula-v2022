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

import android.content.Context
import android.content.Intent
import com.github.javiersantos.materialstyleddialogs.MaterialStyledDialog
import com.github.javiersantos.materialstyleddialogs.enums.Style
import es.usc.citius.servando.calendula.CalendulaApp
import es.usc.citius.servando.calendula.database.DB
import es.usc.citius.servando.calendula.R
import es.usc.citius.servando.calendula.activities.MedicinesActivity
import es.usc.citius.servando.calendula.persistence.Medicine
import es.usc.citius.servando.calendula.util.IconUtils
import es.usc.citius.servando.calendula.util.LogUtil

object StockDisplayUtils {


    private const val TAG = "StockDisplayUtils"
    private const val MAX_DAYS = 21

    @JvmStatic
    fun getReadableStockDuration(estimatedEnd: StockCalculator.StockEnd, ctx: Context): String =
        when (estimatedEnd) {
            is StockCalculator.StockEnd.OnDate -> {
                LogUtil.d(
                    TAG,
                    "updateStockText: estimated end date is " + estimatedEnd.date.toString("dd/MM")
                )
                if (estimatedEnd.days < MAX_DAYS) {
                    ctx.resources.getQuantityString(
                        R.plurals.stock_enough_for_days, estimatedEnd.days.toInt(), estimatedEnd.days
                    )
                } else {
                    val weeks = (estimatedEnd.days / 7).toInt()
                    val days = (estimatedEnd.days % 7).toInt()
                    ctx.getString(
                        R.string.stock_enough_for_weeks_days,
                        ctx.resources.getQuantityString(R.plurals.stock_duration_weeks, weeks, weeks),
                        ctx.resources.getQuantityString(R.plurals.stock_duration_days, days, days)
                    )
                }
            }
            StockCalculator.StockEnd.OverMax -> ctx.getString(R.string.stock_enough_for_upper_limit)
            StockCalculator.StockEnd.Unavailable -> ctx.getString(R.string.stock_estimate_unavailable)
        }


    /**
     * Stock alerts are displayed a moment after publication. Recheck the
     * current patient and medicine owner before opening a delayed warning,
     * so switching profiles cannot display another patient's inventory.
     */
    internal fun ownerMatchesActivePatient(
        emittedOwnerId: Long?, storedOwnerId: Long?, activePatientId: Long?
    ): Boolean = emittedOwnerId != null && emittedOwnerId > 0L
        && emittedOwnerId == storedOwnerId && emittedOwnerId == activePatientId

    /** Unsafe/disabled amounts are not suitable for an inventory warning. */
    internal fun displayableQuantity(stock: Float?): String? {
        if (stock == null || !stock.isFinite() || stock < 0f) return null
        return if (stock <= Int.MAX_VALUE && stock == stock.toInt().toFloat())
            stock.toInt().toString() else stock.toString()
    }

    @JvmStatic
    fun showStockRunningOutDialog(context: Context, m: Medicine, days: Long?) {
        val medicineId = m.id ?: return
        if (medicineId <= 0L) return
        // Capture the message's original stock and owner before ORMLite may
        // rehydrate a cached Medicine object during the DB read.
        val emittedStock = m.stock
        val emittedOwnerId = m.patient?.id
        val activePatientId = DB.patients().getActive(context)?.id
        val stored = DB.medicines().findById(medicineId) ?: return
        if (DB.medicines().refresh(stored) != 1
            || !ownerMatchesActivePatient(
                emittedOwnerId, stored.patient?.id, activePatientId
            ) || emittedStock != stored.stock) {
            return
        }
        val remainingDays = days?.takeIf { it >= 0L && it <= Int.MAX_VALUE }?.toInt()
            ?: return
        val stock = stored.stock ?: return
        val quantity = displayableQuantity(stock) ?: return
        val presentation = stored.presentation ?: return
        val name = stored.name ?: return

        val msg = context.resources.getQuantityString(
            R.plurals.stock_running_out_dialog_message,
            remainingDays,
            quantity,
            presentation.units(context.resources, stock.toDouble()),
            name,
            remainingDays
        )

        MaterialStyledDialog.Builder(context)
            .setTitle(context.getString(R.string.stock_running_out_dialog_title, name))
            .setStyle(Style.HEADER_WITH_ICON)
            .setIcon(IconUtils.icon(context, presentation.icon(), R.color.white, 48))
            .setHeaderColor(R.color.android_orange_dark)
            .withDialogAnimation(true)
            .setDescription(msg)
            .setPositiveText(R.string.manage_stock)
            .onPositive { _, _ ->
                // The patient may change WHILE this dialog is visible. An
                // authorization check at display time is not sufficient to
                // launch an editor for another patient's medicine later.
                val currentPatientId = DB.patients().getActive(context)?.id
                val latestMedicine = DB.medicines().findById(medicineId)
                if (latestMedicine == null || DB.medicines().refresh(latestMedicine) != 1
                    || !ownerMatchesActivePatient(
                        emittedOwnerId, latestMedicine.patient?.id, currentPatientId
                    )) {
                    return@onPositive
                }
                val i = Intent(context, MedicinesActivity::class.java)
                i.putExtra(CalendulaApp.INTENT_EXTRA_MEDICINE_ID, medicineId)
                context.startActivity(i)
            }
            .setNeutralText(R.string.tutorial_understood)
            .onNeutral { dialog, _ -> dialog.dismiss()
            }
            .show()
    }


}
