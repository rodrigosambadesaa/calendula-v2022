/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/** Pure JVM tests: no actual medical file, bitmap or network required. */
public class PrivatePdfPageSessionTest {
    @Test
    public void pngBufferAllowsExactLimitAndRejectsNextByte() {
        PrivatePdfPageSession.BoundedPngBuffer output =
                new PrivatePdfPageSession.BoundedPngBuffer(4);
        output.write(new byte[]{10, 20, 30}, 0, 3);
        output.write(40);
        assertEquals(4, output.size());
        try {
            output.write(50);
            fail("PNG encoder must stop before growing beyond its byte budget");
        } catch (IllegalStateException expected) {
            assertEquals(4, output.size());
        }
    }

    @Test
    public void oversizedChunkIsRejectedWithoutPartiallyOverwritingTheBuffer() {
        PrivatePdfPageSession.BoundedPngBuffer output =
                new PrivatePdfPageSession.BoundedPngBuffer(4);
        output.write(new byte[]{1, 2}, 0, 2);
        try {
            output.write(new byte[]{3, 4, 5}, 0, 3);
            fail("A multi-byte chunk may not exceed the remaining budget");
        } catch (IllegalStateException expected) {
            assertEquals(2, output.size());
        }
        output.write(new byte[]{3, 4}, 0, 2);
        assertEquals(4, output.size());
        output.write(new byte[0], 0, 0);
        assertEquals(4, output.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativePngBudgetIsRejected() {
        new PrivatePdfPageSession.BoundedPngBuffer(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void oversizedPngBudgetIsRejected() {
        new PrivatePdfPageSession.BoundedPngBuffer(
                PrivatePdfPageSession.MAX_PNG_BYTES + 1);
    }
}
