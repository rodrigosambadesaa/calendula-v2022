package es.usc.citius.servando.calendula.util;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.LocalDate;
import org.joda.time.DateTimeConstants;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;

import androidx.core.util.Pair;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DispensationInfoEntity;

import static org.junit.Assert.*;

/** Synthetic pickup authorization intervals only; no patient or pharmacy records. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DispensationProviderWindowSafetyTest {
    private static DispensationInfoEntity window(DateTime start, DateTime end) {
        DispensationInfoEntity med = new DispensationInfoEntity();
        med.setDispenseInterval(new Interval(start, end));
        return med;
    }

    @Test
    public void localTenDayRecommendationCannotExceedShorterProviderWindow() {
        LocalDate first = new LocalDate(2026, 10, 28);
        DateTime start = first.toDateTimeAtStartOfDay();
        DispensationInfoEntity med = window(start, start.plusDays(2));
        assertTrue(DispensationInfoStore.eligibleWithinProviderInterval(med, first, 10));
        assertTrue(DispensationInfoStore.eligibleWithinProviderInterval(med,
                first.plusDays(1), 10));
        assertFalse("Provider end is exclusive even if local horizon is longer",
                DispensationInfoStore.eligibleWithinProviderInterval(med,
                        first.plusDays(2), 10));
        assertFalse(DispensationInfoStore.eligibleWithinProviderInterval(med,
                first.minusDays(1), 10));
        med.setDispensed(true);
        assertFalse(DispensationInfoStore.eligibleWithinProviderInterval(med, first, 10));
        assertFalse(DispensationInfoStore.eligibleWithinProviderInterval(null, first, 10));
        assertFalse(DispensationInfoStore.eligibleWithinProviderInterval(med, null, 10));
    }

    @Test
    public void localHorizonStillBoundsOverlongProviderAuthorization() {
        LocalDate first = new LocalDate(2026, 10, 28);
        DateTime start = first.toDateTimeAtStartOfDay();
        DispensationInfoEntity med = window(start, start.plusDays(30));
        assertTrue(DispensationInfoStore.eligibleWithinProviderInterval(med,
                first.plusDays(8), 10));
        assertFalse(DispensationInfoStore.eligibleWithinProviderInterval(med,
                first.plusDays(9), 10));
    }

    @Test
    public void recommendationDoesNotInventDayAfterOneDayProviderWindow() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        DateTime start = tomorrow.toDateTimeAtStartOfDay();
        DispensationInfoStore store = new DispensationInfoStore(
                Collections.singletonList(window(start, start.plusDays(1))));
        Pair<LocalDate, java.util.List<DispensationInfoEntity>> recommended = store.getBestDay();
        if (tomorrow.getDayOfWeek() == DateTimeConstants.SUNDAY) {
            assertNull("Only authorized candidate falls on excluded Sunday", recommended);
        } else {
            assertNotNull(recommended);
            assertEquals("Must not recommend later unverified dates",
                    tomorrow, recommended.first);
        }
    }
}
