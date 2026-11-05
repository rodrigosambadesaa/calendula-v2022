/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util.stock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure checks: no UI, alarms, or patient database records. */
class StockWarningPresentationGuardTest {
    @Test
    fun currentMedicationMustBelongToTheCurrentPatient() {
        fun matches(
            emittedId: Long?, emittedCode: String?,
            storedId: Long?, storedCode: String?,
            activeId: Long?, activeCode: String?
        ) = StockDisplayUtils.ownerMatchesActivePatient(
            emittedId, emittedCode, storedId, storedCode, activeId, activeCode
        )
        assertTrue(matches(21L, "synthetic-a", 21L, "synthetic-a", 21L, "synthetic-a"))
        assertFalse(matches(21L, "synthetic-a", 22L, "synthetic-a", 21L, "synthetic-a"))
        assertFalse(matches(21L, "synthetic-a", 21L, "synthetic-a", 22L, "synthetic-a"))
        assertFalse(matches(null, "synthetic-a", 21L, "synthetic-a", 21L, "synthetic-a"))
        assertFalse(matches(21L, "synthetic-a", null, "synthetic-a", 21L, "synthetic-a"))
        assertFalse(matches(21L, "synthetic-a", 21L, "synthetic-a", null, "synthetic-a"))
        assertFalse(matches(0L, "synthetic-a", 0L, "synthetic-a", 0L, "synthetic-a"))
    }

    @Test
    fun reusedNumericOwnerIdCannotDisplayOrManageOtherPatientStock() {
        fun matches(oldCode: String?, persistedCode: String?, activeCode: String?) =
            StockDisplayUtils.ownerMatchesActivePatient(
                401L, oldCode, 401L, persistedCode, 401L, activeCode
            )

        assertTrue(matches("patient-synthetic-a", "patient-synthetic-a", "patient-synthetic-a"))
        assertFalse("Old notification cannot show replacement patient's inventory",
            matches("patient-synthetic-a", "patient-synthetic-b", "patient-synthetic-b"))
        assertFalse("Changed active patient code must prevent the editor launch",
            matches("patient-synthetic-a", "patient-synthetic-a", "patient-synthetic-b"))
        assertFalse(matches(null, "patient-synthetic-b", "patient-synthetic-b"))
        assertFalse(matches(" ", " ", " "))
        assertFalse(matches("patient-synthetic-a", null, "patient-synthetic-a"))
        assertFalse(matches("patient-synthetic-a", "patient-synthetic-a", null))
    }

    @Test
    fun fractionalStockCannotBeSilentlyRenderedAsZero() {
        assertEquals("0.5", StockDisplayUtils.displayableQuantity(0.5f))
        assertEquals("1.25", StockDisplayUtils.displayableQuantity(1.25f))
        assertEquals("0", StockDisplayUtils.displayableQuantity(0f))
        assertEquals("2", StockDisplayUtils.displayableQuantity(2f))
    }

    @Test
    fun invalidOrDisabledInventorySuppressesMisleadingWarning() {
        assertNull(StockDisplayUtils.displayableQuantity(null))
        assertNull(StockDisplayUtils.displayableQuantity(-1f))
        assertNull(StockDisplayUtils.displayableQuantity(Float.NaN))
        assertNull(StockDisplayUtils.displayableQuantity(Float.POSITIVE_INFINITY))
        assertNull(StockDisplayUtils.displayableQuantity(Float.NEGATIVE_INFINITY))
    }
}
