/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Pure concurrency policy tests: no Android device or patient records. */
public class StockEstimateRequestGuardTest {

    @Test
    public void onlyCurrentMedicineAndCurrentQuantityCanUpdateUi() {
        Object first = new Object();
        Object second = new Object();

        assertTrue(StockEstimateRequestGuard.isCurrent(
                7L, 7L, first, first, 3f, 3f, true));
        assertFalse("Newer async estimate supersedes the older one",
                StockEstimateRequestGuard.isCurrent(
                        6L, 7L, first, first, 3f, 3f, true));
        assertFalse("Selecting another medicine must hide old stock duration",
                StockEstimateRequestGuard.isCurrent(
                        7L, 7L, first, second, 3f, 3f, true));
        assertFalse("Changing remaining stock invalidates the old estimate",
                StockEstimateRequestGuard.isCurrent(
                        7L, 7L, first, first, 3f, 2f, true));
        assertFalse("Disabled inventory must not show background estimates",
                StockEstimateRequestGuard.isCurrent(
                        7L, 7L, first, first, 3f, 3f, false));
    }

    @Test
    public void invalidAndMissingInputsCannotProduceAValidUiUpdate() {
        Object medicine = new Object();
        assertFalse(StockEstimateRequestGuard.isCurrent(
                1L, 1L, null, medicine, 1f, 1f, true));
        assertFalse(StockEstimateRequestGuard.isCurrent(
                1L, 1L, medicine, medicine, Float.NaN, Float.NaN, true));
        assertFalse(StockEstimateRequestGuard.isCurrent(
                1L, 1L, medicine, medicine, Float.POSITIVE_INFINITY,
                Float.POSITIVE_INFINITY, true));
        assertFalse(StockEstimateRequestGuard.isCurrent(
                1L, 1L, medicine, medicine, -1f, -1f, true));
    }
}
