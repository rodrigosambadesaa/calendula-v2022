/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

import org.junit.Test;
import java.math.BigDecimal;

import static org.junit.Assert.*;

/** Pure numeric edge cases across medication stock additions and removals. */
public class MedicineStockInputGuardTest {
    @Test
    public void fractionalAdjustmentsRetainPrecisionAndClampAtZero() {
        assertEquals(0.5f, MedicineStockInputGuard.adjust(
                -1f, new BigDecimal("0.5"), true), 0f);
        assertEquals(1.75f, MedicineStockInputGuard.adjust(
                1.25f, new BigDecimal("0.5"), true), 0f);
        assertEquals(0.75f, MedicineStockInputGuard.adjust(
                1.25f, new BigDecimal("0.5"), false), 0f);
        assertEquals(0f, MedicineStockInputGuard.adjust(
                1.25f, new BigDecimal("3"), false), 0f);
    }

    @Test
    public void rejectsNegativeZeroUnknownAndNonfiniteQuantities() {
        assertNull(MedicineStockInputGuard.adjust(1f, null, true));
        assertNull(MedicineStockInputGuard.adjust(1f, BigDecimal.ZERO, true));
        assertNull(MedicineStockInputGuard.adjust(1f, new BigDecimal("-1"), false));
        assertNull(MedicineStockInputGuard.adjust(-1f, BigDecimal.ONE, false));
        assertNull(MedicineStockInputGuard.adjust(Float.NaN, BigDecimal.ONE, true));
        assertNull(MedicineStockInputGuard.adjust(Float.POSITIVE_INFINITY, BigDecimal.ONE, false));
        assertFalse(MedicineStockInputGuard.usableStock(Float.NaN));
        assertFalse(MedicineStockInputGuard.usableStock(Float.NEGATIVE_INFINITY));
        assertFalse(MedicineStockInputGuard.usableStock(-1f));
        assertTrue(MedicineStockInputGuard.usableStock(0f));
        assertTrue(MedicineStockInputGuard.usableStock(1.5f));
    }

    @Test
    public void rejectsFloatOverflowAndUnderflowFromDecimalInput() {
        assertNull(MedicineStockInputGuard.adjust(
                1f, new BigDecimal("1E999"), true));
        assertNull(MedicineStockInputGuard.adjust(
                1f, new BigDecimal("1E-999"), true));
        assertNull(MedicineStockInputGuard.adjust(
                Float.MAX_VALUE, new BigDecimal("1E30"), true));
    }
}
