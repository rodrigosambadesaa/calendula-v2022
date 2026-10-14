/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

import org.junit.Test;

import static org.junit.Assert.*;

/** Local policy checks; no access to real medication or patient data. */
public class MedicineInfoReadGuardTest {
    @Test
    public void refusesStaleForeignAndDeletedMedicineDetails() {
        assertTrue(MedicineInfoReadGuard.isCurrentOwner(20L, 30L, 20L, 30L, 30L));
        assertFalse(MedicineInfoReadGuard.isCurrentOwner(20L, 30L, 20L, 30L, 31L));
        assertFalse(MedicineInfoReadGuard.isCurrentOwner(20L, 30L, 20L, 31L, 30L));
        assertFalse(MedicineInfoReadGuard.isCurrentOwner(20L, 30L, 21L, 30L, 30L));
        assertFalse(MedicineInfoReadGuard.isCurrentOwner(20L, 30L, null, 30L, 30L));
        assertFalse(MedicineInfoReadGuard.isCurrentOwner(0L, 30L, 0L, 30L, 30L));
        assertFalse(MedicineInfoReadGuard.isCurrentOwner(20L, null, 20L, 30L, 30L));
    }

    @Test
    public void stockAmountNeverDisplaysUnverifiedQuantities() {
        assertNull(MedicineInfoReadGuard.formatStock(null));
        assertNull(MedicineInfoReadGuard.formatStock(-1f));
        assertNull(MedicineInfoReadGuard.formatStock(-0.01f));
        assertNull(MedicineInfoReadGuard.formatStock(Float.NaN));
        assertNull(MedicineInfoReadGuard.formatStock(Float.POSITIVE_INFINITY));
        assertNull(MedicineInfoReadGuard.formatStock(Float.NEGATIVE_INFINITY));
        assertEquals("0", MedicineInfoReadGuard.formatStock(0f));
        assertEquals("1", MedicineInfoReadGuard.formatStock(1f));
        assertEquals("0.5", MedicineInfoReadGuard.formatStock(0.5f));
        assertEquals("1.75", MedicineInfoReadGuard.formatStock(1.75f));
        assertEquals(Float.toString(Float.MAX_VALUE),
                MedicineInfoReadGuard.formatStock(Float.MAX_VALUE));
    }
}
