/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util.medicine;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.PatientAlert;
import es.usc.citius.servando.calendula.persistence.Presentation;

import static org.junit.Assert.*;

/** Pure JVM regression tests; no patient data or Android context. */
public class MedicineSortUtilTest {
    @Test
    public void missingNamesSortAfterUsableNames() {
        Comparator<Medicine> cmp =
                MedicineSortUtil.MedSortType.NAME.comparator();
        Medicine normal = new Medicine("Aspirin", Presentation.PILLS);
        Medicine unnamed = new Medicine(null, Presentation.PILLS);
        assertTrue(cmp.compare(normal, unnamed) < 0);
        assertTrue(cmp.compare(unnamed, normal) > 0);
        assertEquals(0, cmp.compare(unnamed, unnamed));
        assertTrue(cmp.compare(null, normal) > 0);
        assertTrue(cmp.compare(normal, null) < 0);
        assertEquals(0, cmp.compare(null, null));
    }

    @Test
    public void missingPresentationSortsLastWithoutThrowing() {
        Comparator<Medicine> cmp =
                MedicineSortUtil.MedSortType.PRESENTATION.comparator();
        Medicine known = new Medicine("Known", Presentation.PILLS);
        Medicine missing = new Medicine("Legacy", null);
        assertTrue(cmp.compare(known, missing) < 0);
        assertTrue(cmp.compare(missing, known) > 0);
        assertEquals(0, cmp.compare(missing, missing));
    }

    @Test
    public void alertSortQueriesEachMedicineOncePerSortAndKeepsSeverity() {
        Medicine high = new Medicine("High");
        Medicine low = new Medicine("Low");
        Medicine none = new Medicine("None");
        AtomicInteger reads = new AtomicInteger();
        Comparator<Medicine> cmp = MedicineSortUtil.alertComparatorWith(m -> {
            reads.incrementAndGet();
            if (m == high) return Arrays.asList(alert(1), alert(3));
            if (m == low) return Collections.singletonList(alert(2));
            return Collections.emptyList();
        });

        assertTrue(cmp.compare(high, low) < 0);
        assertTrue(cmp.compare(low, none) < 0);
        assertTrue(cmp.compare(high, none) < 0);
        assertEquals("One verified alert read per medicine", 3, reads.get());

        // A new sort gets a fresh snapshot rather than reusing patient alerts
        // from the previous sorting operation.
        Comparator<Medicine> second = MedicineSortUtil.alertComparatorWith(m -> {
            reads.incrementAndGet();
            return Collections.emptyList();
        });
        assertEquals(0, second.compare(high, low));
        assertEquals(5, reads.get());
    }

    private static PatientAlert alert(int level) {
        PatientAlert result = new PatientAlert();
        result.setLevel(level);
        return result;
    }
}
