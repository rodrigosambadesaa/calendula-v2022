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


object StockUpdater {

    /**
     * Persist a stock delta for an event transition. Call this from the same
     * ORMLite transaction that persists EventInstance.Completed.
     *
     * @return true only when stock tracking was enabled and a row was updated.
     * Failures MUST escape so SQLite can roll back both the stock and intake.
     */
    @JvmStatic
    fun applyStockForTransition(intake: EventInstance, previouslyCompleted: Boolean): Boolean {
        require(intake.type == EventType.MEDICATION_INTAKE) {
            "Event instance must be a medication intake"
        }
        if (previouslyCompleted == intake.completed()) return false

        val scheduleId = intake.ref
            ?: throw IllegalStateException("Medication intake references no schedule")
        val schedule = DB.schedules().findById(scheduleId)
            ?: throw IllegalStateException("Medication intake schedule is missing")
        val medicine = schedule.medicine
            ?: throw IllegalStateException("Medication intake has no linked medicine")
        // Never debit a different patient's inventory because an imported
        // event references a wrong or detached schedule.
        val eventPatientId = intake.patient?.id
            ?: throw IllegalStateException("Medication intake has no assigned patient")
        val schedulePatientId = schedule.patient?.id
            ?: throw IllegalStateException("Medication schedule has no assigned patient")
        val medicinePatientId = medicine.patient?.id
        if (eventPatientId != schedulePatientId ||
                (medicinePatientId != null && medicinePatientId != eventPatientId)) {
            throw IllegalStateException("Medication intake has inconsistent patient ownership")
        }
        if (medicine.id == null || DB.medicines().refresh(medicine) != 1) {
            throw IllegalStateException("Medication stock row is missing")
        }
        if (!medicine.stockManagementEnabled()) return false

        val before = medicine.stock
            ?: throw IllegalStateException("Medication stock is unavailable")
        val dose = try {
            intake.getDoubleParam(EventInstance.PARAM_DOSE)
        } catch (malformed: RuntimeException) {
            throw IllegalStateException("Medication stock dosage is missing or malformed", malformed)
        }
        if (!dose.isFinite() || dose <= 0.0) {
            throw IllegalStateException("Medication stock dosage must be finite and positive")
        }
        val delta = if (intake.completed()) -dose else dose
        val updated = before.toDouble() + delta
        if (!updated.isFinite() || updated < 0.0 || updated > Float.MAX_VALUE) {
            throw IllegalStateException("Medication stock adjustment is outside valid bounds")
        }

        medicine.stock = updated.toFloat()
        // Bypass MedicineDao.save(): it posts alerts before COMMIT.
        // Restore this in-memory object on failed SQL updates; the outer
        // transaction handles persisted rollback.
        try {
            if (DB.medicines().update(medicine) != 1) {
                throw IllegalStateException("Expected one medicine stock row to change")
            }
        } catch (failure: Exception) {
            medicine.stock = before
            throw failure
        }
        return true
    }

    @JvmStatic
    fun updateStockForIntake(intake: EventInstance, fireEvent: Boolean) {
        val id = intake.id ?: throw IllegalArgumentException("Persisted intake ID required")
        val original = DB.eventInstances().findById(id)
            ?: throw IllegalStateException("Medication intake is no longer persisted")
        if (applyStockForTransition(intake, original.completed()) && fireEvent) {
            fireEvent()
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