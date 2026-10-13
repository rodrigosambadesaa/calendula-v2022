/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util.stock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure tests; never access SQLite or actual patient treatment plans. */
class MedicineDoseSumSafetyTest {

    @Test
    fun ordinaryMultipleDailyDosesRetainTheirExactTotal() {
        var sum = 0f
        sum = MedicineScheduleStockProvider.checkedDoseSum(sum, 0.5)
        sum = MedicineScheduleStockProvider.checkedDoseSum(sum, 1.25)
        sum = MedicineScheduleStockProvider.checkedDoseSum(sum, 0.75)
        assertEquals(2.5f, sum, 0.00001f)
    }

    @Test
    fun negativeLegacyDoseCannotCancelAnEarlierPositiveDose() {
        val first = MedicineScheduleStockProvider.checkedDoseSum(3f, 2.0)
        assertTrue(MedicineScheduleStockProvider.checkedDoseSum(first, -1.0).isNaN())
        assertTrue(MedicineScheduleStockProvider.checkedDoseSum(0f, -0.1).isNaN())
    }

    @Test
    fun nonFiniteLegacyDoseCannotLookLikeAdequateSupply() {
        for (bad in doubleArrayOf(
                Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                Double.MAX_VALUE)) {
            assertTrue("Invalid dose must propagate as unavailable",
                MedicineScheduleStockProvider.checkedDoseSum(2f, bad).isNaN())
        }
        assertTrue(MedicineScheduleStockProvider.checkedDoseSum(
            Float.NaN, 1.0).isNaN())
    }

    @Test
    fun overflowingFloatAccumulationIsRejectedInsteadOfInfinity() {
        assertTrue(MedicineScheduleStockProvider.checkedDoseSum(
            Float.MAX_VALUE, Float.MAX_VALUE.toDouble()).isNaN())
    }

    @Test
    fun zeroDailyConsumptionIsStillSupported() {
        assertEquals(0f, MedicineScheduleStockProvider.checkedDoseSum(
            0f, 0.0), 0f)
    }
}
