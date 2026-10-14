/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Verify comparator antisymmetry and missing provider timestamps. */
public class ActiveMedicationOrderingTest {
    @Test
    public void bothInactiveRowsCompareSymmetrically() {
        assertEquals(0, ActiveMedicationOrdering.compare(true, true, 2L, 2L));
        assertEquals(0, ActiveMedicationOrdering.compare(true, true, null, null));
        assertEquals(0, ActiveMedicationOrdering.compare(false, false, 1L, 1L));
        int a = ActiveMedicationOrdering.compare(true, true, 1L, 2L);
        int b = ActiveMedicationOrdering.compare(true, true, 2L, 1L);
        assertEquals(-Integer.signum(a), Integer.signum(b));
    }

    @Test
    public void activeRowsPrecedeInactiveAndLegacyNullDatesSortLast() {
        assertTrue(ActiveMedicationOrdering.compare(false, true, null, 1L) < 0);
        assertTrue(ActiveMedicationOrdering.compare(true, false, 1L, null) > 0);
        assertTrue(ActiveMedicationOrdering.compare(false, false, 1L, null) < 0);
        assertTrue(ActiveMedicationOrdering.compare(true, true, null, 3L) > 0);
        assertTrue(ActiveMedicationOrdering.compare(false, false, 3L, 4L) < 0);
    }

    @Test
    public void orderIsTransitiveForSampleProviderRecords() {
        // All active items, ordered by ascending time; unknown is last.
        long[] known = {1L, 2L, 3L};
        assertTrue(ActiveMedicationOrdering.compare(false, false, known[0], known[1]) < 0);
        assertTrue(ActiveMedicationOrdering.compare(false, false, known[1], known[2]) < 0);
        assertTrue(ActiveMedicationOrdering.compare(false, false, known[0], known[2]) < 0);
    }
}
