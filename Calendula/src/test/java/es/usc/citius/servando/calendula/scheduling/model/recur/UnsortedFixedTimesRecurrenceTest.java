/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling.model.recur;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class UnsortedFixedTimesRecurrenceTest {
    @Test
    public void laterListedMorningDoseIsNotLostAfterEveningDoseExceedsWindow() {
        LocalDate today = new LocalDate(2026, 10, 14);
        RecurringEvent recurrence = new RecurringEvent.Builder()
                .repeatDaily().from(today)
                .atFixedTime(new LocalTime(20, 0))
                .atFixedTime(new LocalTime(9, 0)).build();
        DateTime start = today.toDateTimeAtStartOfDay();
        List<RecurringEvent.EventTimeInfo> occurrences =
                recurrence.occurrencesIn(start, start.plusHours(12));
        assertEquals("An unsorted evening occurrence must not hide a valid morning dose",
                1, occurrences.size());
        assertEquals(new LocalTime(9, 0),
                occurrences.get(0).time().toLocalTime());
    }

    @Test
    public void bothUnsortedIntakeTimesStillAppearAcrossCompleteDay() {
        LocalDate today = new LocalDate(2026, 10, 14);
        RecurringEvent recurrence = new RecurringEvent.Builder()
                .repeatDaily().from(today)
                .atFixedTime(new LocalTime(20, 0))
                .atFixedTime(new LocalTime(9, 0)).build();
        DateTime start = today.toDateTimeAtStartOfDay();
        List<RecurringEvent.EventTimeInfo> occurrences =
                recurrence.occurrencesIn(start, start.plusDays(1));
        assertEquals(2, occurrences.size());
    }
}
