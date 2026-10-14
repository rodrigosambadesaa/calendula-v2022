/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import org.junit.Test;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PdfBitmapBoundsTest {
    @Test
    public void normalPagesGetLegibleDoubleResolution() {
        int[] a4 = PdfBitmapBounds.fit(595, 842);
        assertEquals(1190, a4[0]);
        assertEquals(1684, a4[1]);
        int[] thumbnail = PdfBitmapBounds.fit(1, 1);
        assertEquals(2, thumbnail[0]);
        assertEquals(2, thumbnail[1]);
    }

    @Test
    public void hugeOrNarrowPagesAreBoundedWithoutIntegerOverflow() {
        assertBounded(Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertBounded(Integer.MAX_VALUE, 1);
        assertBounded(1, Integer.MAX_VALUE);
        assertBounded(4000, 4000);
        assertBounded(100, 2_000_000);
        assertBounded(500, 1000);
    }

    @Test
    public void randomizedPositivePageDimensionsCannotExceedMemoryBudget() {
        Random random = new Random(107372L);
        for (int i = 0; i < 12_000; i++) {
            assertBounded(1 + random.nextInt(Integer.MAX_VALUE - 1),
                    1 + random.nextInt(Integer.MAX_VALUE - 1));
        }
    }

    @Test
    public void zeroOrNegativePageSizeIsRejected() {
        for (int[] dimensions : new int[][]{{0, 42}, {1, 0}, {-1, 42}, {1, -3}}) {
            try {
                PdfBitmapBounds.fit(dimensions[0], dimensions[1]);
                fail("Invalid PDF page dimensions should be rejected");
            } catch (IllegalArgumentException expected) {
                // Valid rejection.
            }
        }
    }

    private static void assertBounded(int width, int height) {
        int[] actual = PdfBitmapBounds.fit(width, height);
        assertTrue(actual[0] >= 1 && actual[0] <= PdfBitmapBounds.MAX_SIDE);
        assertTrue(actual[1] >= 1 && actual[1] <= PdfBitmapBounds.MAX_SIDE);
        assertTrue((long) actual[0] * actual[1] <= PdfBitmapBounds.MAX_PIXELS);
    }
}
