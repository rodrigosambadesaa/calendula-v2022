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

package es.usc.citius.servando.calendula.util.stock

import es.usc.citius.servando.calendula.database.DB
import es.usc.citius.servando.calendula.scheduling.model.EventInstance
import es.usc.citius.servando.calendula.scheduling.model.EventType
import es.usc.citius.servando.calendula.util.LogUtil
import es.usc.citius.servando.calendula.util.alerts.StockAlertHandler


object StockUpdater {

    private const val TAG = "StockUpdater"

    @JvmStatic
    fun updateStockForIntake(intake: EventInstance, fireEvent: Boolean) {
        if (intake.type != EventType.MEDICATION_INTAKE) {
            throw IllegalArgumentException("Event instance must be a medication intake")
        }

        val s = DB.schedules().findById(intake.ref)
        val m = s.medicine
        if (m.stockManagementEnabled()) {
            // get original value
            val original = DB.eventInstances().findById(intake.id)
            // ensure checked status has changed
            val updateStock = original.completed() != intake.completed()

            if (updateStock) {
                try {
                    var amount = intake.getDoubleParam(EventInstance.PARAM_DOSE).toFloat()
                    // if intake is check we need to subtract the ammount
                    // in other case we need to sum it to the current stock
                    if (intake.completed()) {
                        amount *= -1
                    }
                    m.stock = m.stock + amount
                    DB.medicines().save(m)

                    if (fireEvent) {
                        fireEvent()
                    }

                } catch (e: Exception) {
                    LogUtil.e(TAG, "An error occurred updating stock", e)
                }
            }
        }
    }


    @JvmStatic
    fun updateStockForIntakes(intakes: Collection<EventInstance>, fireEvent: Boolean) {
        for (i in intakes) {
            updateStockForIntake(i, false)
        }
        if(fireEvent) {
            fireEvent()
        }
    }

    @JvmStatic
    private fun fireEvent(){
        DB.medicines().fireEvent()
    }

}