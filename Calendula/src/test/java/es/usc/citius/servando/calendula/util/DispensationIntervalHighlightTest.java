package es.usc.citius.servando.calendula.util;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Interval;
import org.joda.time.LocalDate;
import org.junit.Test;

import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;

/** Synthetic date windows only: no provider requests or clinical writes. */
public class DispensationIntervalHighlightTest {
    @Test
    public void exclusiveMidnightEndDoesNotMarkAnUnauthorizedDay() {
        DateTime start = new DateTime(2026, 10, 27, 9, 0);
        List<Date> highlighted = DispensationIntervalHighlight.dates(
                new Interval(start, start.withTimeAtStartOfDay().plusDays(2)));
        assertEquals(2, highlighted.size());
        assertEquals(new LocalDate(2026, 10, 27), LocalDate.fromDateFields(highlighted.get(0)));
        assertEquals(new LocalDate(2026, 10, 28), LocalDate.fromDateFields(highlighted.get(1)));
    }

    @Test
    public void partialEndDayIsIncludedBecauseIntervalStillIntersectsIt() {
        DateTime start = new DateTime(2026, 10, 28, 22, 0);
        List<Date> highlighted = DispensationIntervalHighlight.dates(
                new Interval(start, start.plusHours(4)));
        assertEquals(2, highlighted.size());
    }

    @Test
    public void zeroLengthOrAbsentIntervalNeverCreatesHighlights() {
        DateTime now = new DateTime(2026, 10, 27, 0, 0);
        assertTrue(DispensationIntervalHighlight.dates(null).isEmpty());
        assertTrue(DispensationIntervalHighlight.dates(new Interval(now, now)).isEmpty());
    }

    @Test
    public void localDatesSpanDaylightSavingClockChangesExactlyOnce() {
        DateTimeZone zone = DateTimeZone.forID("Europe/Madrid");
        DateTime start = new DateTime(2026, 10, 24, 0, 0, zone);
        DateTime end = new DateTime(2026, 10, 27, 0, 0, zone);
        List<Date> highlighted = DispensationIntervalHighlight.dates(
                new Interval(start, end));
        assertEquals(3, highlighted.size());
        assertEquals(new LocalDate(2026, 10, 24), LocalDate.fromDateFields(highlighted.get(0)));
        assertEquals(new LocalDate(2026, 10, 26), LocalDate.fromDateFields(highlighted.get(2)));
    }
}
