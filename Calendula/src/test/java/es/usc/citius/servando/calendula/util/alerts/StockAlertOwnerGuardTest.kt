/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util.alerts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure identity guard tests; no real patient records. */
class StockAlertOwnerGuardTest {
    @Test
    fun matchingPersistedMedicineAndPatientAreAllowed() {
        assertTrue(StockAlertHandler.samePersistedMedicineOwner(100L, 200L, 100L, 200L))
    }

    @Test
    fun missingStaleAndCrossPatientOwnerAreRejected() {
        assertFalse(StockAlertHandler.samePersistedMedicineOwner(null, 200L, 100L, 200L))
        assertFalse(StockAlertHandler.samePersistedMedicineOwner(100L, null, 100L, 200L))
        assertFalse(StockAlertHandler.samePersistedMedicineOwner(0L, 200L, 0L, 200L))
        assertFalse(StockAlertHandler.samePersistedMedicineOwner(100L, -1L, 100L, -1L))
        assertFalse(StockAlertHandler.samePersistedMedicineOwner(100L, 200L, 101L, 200L))
        assertFalse(StockAlertHandler.samePersistedMedicineOwner(100L, 200L, 100L, 201L))
    }
}
