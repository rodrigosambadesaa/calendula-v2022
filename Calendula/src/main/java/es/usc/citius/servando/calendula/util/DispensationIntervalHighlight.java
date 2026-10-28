package es.usc.citius.servando.calendula.util;

import org.joda.time.Interval;
import org.joda.time.LocalDate;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Calendar-only highlighting of days intersecting a verified pickup interval. */
public final class DispensationIntervalHighlight {
    private DispensationIntervalHighlight() { }

    /**
     * Intervals are [start, end). Never mark the day at end when the provider
     * ends at midnight, and never add a gratuitous extra day. Work in local
     * calendar dates to avoid adding fixed 24-hour instants across DST.
     */
    public static List<Date> dates(Interval providerInterval) {
        List<Date> dates = new ArrayList<>();
        if (providerInterval == null || !providerInterval.getEnd()
                .isAfter(providerInterval.getStart())) {
            return dates;
        }
        LocalDate first = providerInterval.getStart().toLocalDate();
        LocalDate last = providerInterval.getEnd().minusMillis(1).toLocalDate();
        for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
            dates.add(day.toDate());
        }
        return dates;
    }
}
