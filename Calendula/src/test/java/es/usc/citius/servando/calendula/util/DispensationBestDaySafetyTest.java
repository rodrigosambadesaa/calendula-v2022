package es.usc.citius.servando.calendula.util;

import androidx.core.util.Pair;
import org.joda.time.LocalDate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import es.usc.citius.servando.calendula.healthcareprovider.persistence.DispensationInfoEntity;
import static org.junit.Assert.*;

/** Deterministic synthetic candidates; no actual medicines or pickup dates. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DispensationBestDaySafetyTest {
    private static final LocalDate SATURDAY = new LocalDate(2026, 10, 31);
    private static final LocalDate SUNDAY = new LocalDate(2026, 11, 1);
    private static final LocalDate MONDAY = new LocalDate(2026, 11, 2);

    @Test
    public void sundayMustNotBeSilentlyMovedToAnIneligibleSaturday() {
        DispensationInfoEntity sundayOnly = new DispensationInfoEntity();
        Map<LocalDate, List<DispensationInfoEntity>> days = new HashMap<>();
        days.put(SUNDAY, Collections.singletonList(sundayOnly));
        assertNull("No valid open pickup date should be fabricated",
                DispensationInfoStore.chooseBestNonSundayPickup(days));
        DispensationInfoEntity saturdayMed = new DispensationInfoEntity();
        days.put(SATURDAY, Collections.singletonList(saturdayMed));
        Pair<LocalDate, List<DispensationInfoEntity>> chosen =
                DispensationInfoStore.chooseBestNonSundayPickup(days);
        assertNotNull(chosen);
        assertEquals(SATURDAY, chosen.first);
        assertEquals(1, chosen.second.size());
        assertSame(saturdayMed, chosen.second.get(0));
        assertNotSame("Do not show a Sunday-only medicine as eligible on Saturday",
                sundayOnly, chosen.second.get(0));
    }

    @Test
    public void chooseHighestNumberOfEligibleMedicinesAndLatestEligibleTie() {
        DispensationInfoEntity a = new DispensationInfoEntity();
        DispensationInfoEntity b = new DispensationInfoEntity();
        Map<LocalDate, List<DispensationInfoEntity>> days = new HashMap<>();
        days.put(SATURDAY, Collections.singletonList(a));
        days.put(SUNDAY, Arrays.asList(a, b, new DispensationInfoEntity()));
        days.put(MONDAY, Arrays.asList(a, b));
        Pair<LocalDate, List<DispensationInfoEntity>> chosen =
                DispensationInfoStore.chooseBestNonSundayPickup(days);
        assertNotNull(chosen);
        assertEquals(MONDAY, chosen.first);
        assertEquals(2, chosen.second.size());
        assertSame(a, chosen.second.get(0));
        assertSame(b, chosen.second.get(1));
        assertNotSame("Output must not expose the input list for mutation",
                days.get(MONDAY), chosen.second);

        days.put(SATURDAY, Arrays.asList(a, b));
        assertEquals(MONDAY,
                DispensationInfoStore.chooseBestNonSundayPickup(days).first);
    }

    @Test
    public void missingOrCorruptCandidateDatesNeverProduceRecommendations() {
        assertNull(DispensationInfoStore.chooseBestNonSundayPickup(null));
        assertNull(DispensationInfoStore.chooseBestNonSundayPickup(Collections.emptyMap()));
        Map<LocalDate, List<DispensationInfoEntity>> days = new HashMap<>();
        days.put(null, new ArrayList<>());
        days.put(SATURDAY, Collections.emptyList());
        assertNull(DispensationInfoStore.chooseBestNonSundayPickup(days));
    }
}
