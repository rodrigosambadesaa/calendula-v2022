/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities.schedules;

import java.math.BigDecimal;

/**
 * Exact manual hourly recurrence entry. UI number-picker positions and
 * persisted hourly recurrence only support whole hours from 1 through 72.
 */
final class HourlyIntervalInputPolicy {
    private static final int MIN_HOURS = 1;
    private static final int MAX_HOURS = 72;

    private HourlyIntervalInputPolicy() { }

    static Integer validWholeHoursOrNull(BigDecimal input) {
        if (input == null) {
            return null;
        }
        try {
            int value = input.intValueExact();
            return value >= MIN_HOURS && value <= MAX_HOURS ? value : null;
        } catch (ArithmeticException fractionalOrOverflow) {
            return null;
        }
    }
}
