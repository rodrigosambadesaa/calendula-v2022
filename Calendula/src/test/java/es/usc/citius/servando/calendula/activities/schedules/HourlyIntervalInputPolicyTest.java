/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities.schedules;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class HourlyIntervalInputPolicyTest {
    @Test
    public void acceptsWholeHoursExactlyAtAndWithinLimits() {
        assertEquals(Integer.valueOf(1), HourlyIntervalInputPolicy.validWholeHoursOrNull(new BigDecimal("1")));
        assertEquals(Integer.valueOf(8), HourlyIntervalInputPolicy.validWholeHoursOrNull(new BigDecimal("8.000")));
        assertEquals(Integer.valueOf(72), HourlyIntervalInputPolicy.validWholeHoursOrNull(new BigDecimal("72")));
    }

    @Test
    public void rejectsFractionalZeroNegativeAndOutOfRangeHours() {
        for (String value : new String[]{"0", "-1", "0.5", "1.5", "8.1", "72.01", "73", "-72.0"}) {
            assertNull(value, HourlyIntervalInputPolicy.validWholeHoursOrNull(new BigDecimal(value)));
        }
        assertNull(HourlyIntervalInputPolicy.validWholeHoursOrNull(null));
    }

    @Test
    public void rejectsValuesThatWouldWrapOrTruncateBigDecimalIntValue() {
        for (String value : new String[]{
                "4294967297", "2147483648", "9999999999999999999999999999999",
                "8.9999999999999999999999", "999999999999.5"}) {
            assertNull(value, HourlyIntervalInputPolicy.validWholeHoursOrNull(new BigDecimal(value)));
        }
    }
}
