/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments.dosePickers;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DoseInputGuardTest {
    @Test
    public void parsesPositiveDecimalDosesInBothSupportedSeparators() {
        assertEquals(1.5d, DoseInputGuard.parsePositive("1.5"), 0.000001d);
        assertEquals(1.5d, DoseInputGuard.parsePositive("1,5"), 0.000001d);
        assertEquals(0.25d, DoseInputGuard.parsePositive(",25"), 0.000001d);
        assertEquals(2.0d, DoseInputGuard.parsePositive(" 2. "), 0.000001d);
        assertEquals(1.0d, DoseInputGuard.parsePositive("1"), 0.000001d);
    }

    @Test
    public void graphicalPickersMustRejectZeroAndNonFiniteValues() {
        org.junit.Assert.assertFalse(DoseInputGuard.isPositiveFinite(0d));
        org.junit.Assert.assertFalse(DoseInputGuard.isPositiveFinite(-1d));
        org.junit.Assert.assertFalse(DoseInputGuard.isPositiveFinite(Double.NaN));
        org.junit.Assert.assertFalse(DoseInputGuard.isPositiveFinite(Double.POSITIVE_INFINITY));
        org.junit.Assert.assertFalse(DoseInputGuard.isPositiveFinite(Double.NEGATIVE_INFINITY));
        org.junit.Assert.assertTrue(DoseInputGuard.isPositiveFinite(.125d));
        org.junit.Assert.assertTrue(DoseInputGuard.isPositiveFinite(50d));
    }

    @Test
    public void liquidSliderMayOnlySubmitWhatItsVisibleThumbRepresents() {
        assertEquals(0, DoseInputGuard.liquidSliderValue(0d, 50));
        assertEquals(0, DoseInputGuard.liquidSliderValue(-10d, 50));
        assertEquals(0, DoseInputGuard.liquidSliderValue(Double.NaN, 50));
        assertEquals(0, DoseInputGuard.liquidSliderValue(Double.POSITIVE_INFINITY, 50));
        assertEquals(0, DoseInputGuard.liquidSliderValue(.75d, 50));
        assertEquals(1, DoseInputGuard.liquidSliderValue(1.75d, 50));
        assertEquals(50, DoseInputGuard.liquidSliderValue(999d, 50));
        assertEquals(0, DoseInputGuard.liquidSliderValue(5d, 0));
    }

    @Test
    public void partialAndInvalidInputCannotBeSavedAsMedicationDose() {
        for (String value : new String[]{"", " ", ".", ",", "-", "-2",
                "0", "0.0", "NaN", "Infinity", "1e309", "1,2.3",
                "1.2.3", "1 2", "+1", "1,000,000", "1/2"}) {
            assertNull("Invalid dose accepted: " + value,
                    DoseInputGuard.parsePositive(value));
        }
        assertNull(DoseInputGuard.parsePositive(null));
    }

    @Test
    public void overflowingOrUnderflowingNumbersNeverBecomeNonFiniteOrZero() {
        assertNull(DoseInputGuard.parsePositive(
                "99999999999999999999999999999999999999999999999999999999999999999999999999999999999"
                + "99999999999999999999999999999999999999999999999999999999999999999999999999999999"
                + "99999999999999999999999999999999999999999999999999999999999999999999999999999999"
                + "99999999999999999999999999999999999999999999999999999999999999999999999999999999"));
    }
}
