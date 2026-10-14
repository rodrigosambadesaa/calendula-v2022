/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling.model.recur;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.joda.time.LocalTime;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class CyclicMedicationDstTest {
    private static final DateTimeZone MADRID = DateTimeZone.forID("Europe/Madrid");

    private static DateTime local(int month, int day) {
        return new DateTime(2026, month, day, 8, 0, 0, 0, MADRID);
    }

    @Test
    public void fullCyclicSchedulerDoesNotScheduleTheRestDayAcrossDst() throws Exception {
        DateTime first = local(3, 28);
        RecurringEvent recurring = new RecurringEvent.Builder()
                .repeatCyclic(1, 1).from(first)
                .atFixedTime(new LocalTime(8, 0)).build();
        java.util.List<RecurringEvent.EventTimeInfo> occurrences =
                recurring.occurrencesIn(first, local(3, 31));
        org.junit.Assert.assertEquals("Only March 28 and March 30 may be active",
                2, occurrences.size());
        org.junit.Assert.assertEquals(28, occurrences.get(0).time().getDayOfMonth());
        org.junit.Assert.assertEquals(30, occurrences.get(1).time().getDayOfMonth());
    }

    @Test
    public void springClockAdvanceDoesNotCreateAnExtraActiveDay() {
        // March 29 is only 23 elapsed hours after March 28 at 08:00.
        // Nevertheless it is calendar day 2 (rest) in a 1+1-day cycle.
        DateTime first = local(3, 28);
        assertTrue(RecurringEvent.isActiveOnCycleDay(first, first, 1, 1));
        assertFalse(RecurringEvent.isActiveOnCycleDay(first, local(3, 29), 1, 1));
        assertTrue(RecurringEvent.isActiveOnCycleDay(first, local(3, 30), 1, 1));
    }

    @Test
    public void autumnClockRetardationKeepsLocalCalendarCycle() {
        DateTime first = local(10, 24);
        assertTrue(RecurringEvent.isActiveOnCycleDay(first, first, 1, 1));
        assertFalse(RecurringEvent.isActiveOnCycleDay(first, local(10, 25), 1, 1));
        assertTrue(RecurringEvent.isActiveOnCycleDay(first, local(10, 26), 1, 1));
    }

    @Test
    public void crossTimeZoneAndOrdinaryCyclesRemainConsistent() {
        DateTime first = local(3, 21);
        DateTime second = local(3, 22).withZone(DateTimeZone.UTC);
        assertTrue(RecurringEvent.isActiveOnCycleDay(first, first, 2, 3));
        assertTrue(RecurringEvent.isActiveOnCycleDay(first, second, 2, 3));
        assertFalse(RecurringEvent.isActiveOnCycleDay(first, local(3, 23), 2, 3));
        assertTrue(RecurringEvent.isActiveOnCycleDay(first, local(3, 26), 2, 3));
        assertFalse(RecurringEvent.isActiveOnCycleDay(first, first.minusMinutes(1), 2, 3));
    }

    @Test(expected = RecurringEvent.InvalidRecurringEventException.class)
    public void builderCannotPersistOverflowingCycleLength() throws Exception {
        new RecurringEvent.Builder().repeatCyclic(Integer.MAX_VALUE, 1)
                .from(local(3, 28)).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidRestDaysDoNotBecomeDivisionByZero() {
        RecurringEvent.isActiveOnCycleDay(local(3, 28), local(3, 29), 0, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void cycleLengthIntegerOverflowIsRejected() {
        RecurringEvent.isActiveOnCycleDay(local(3, 28), local(3, 29),
                Integer.MAX_VALUE, Integer.MAX_VALUE);
    }
}
