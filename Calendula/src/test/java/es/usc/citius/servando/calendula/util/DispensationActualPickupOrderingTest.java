package es.usc.citius.servando.calendula.util;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.LocalDateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DispensationInfoEntity;

import static org.junit.Assert.*;

/** Synthetic provider records; no external history, database or stock writes. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DispensationActualPickupOrderingTest {

    private DispensationInfoEntity pickup(int windowStart, boolean dispensed,
                                           LocalDateTime actual) {
        DispensationInfoEntity entry = new DispensationInfoEntity();
        entry.setDispenseInterval(new Interval(
                new DateTime(2026, 10, windowStart, 0, 0),
                new DateTime(2026, 10, windowStart + 1, 0, 0)));
        entry.setDispensed(dispensed);
        entry.setDispensedDateTime(actual);
        return entry;
    }

    @Test
    public void lastPickupUsesActualDispensedDateNotLatestEligibleInterval() {
        DispensationInfoEntity latestActual = pickup(1, true,
                new LocalDateTime(2026, 10, 20, 15, 30));
        DispensationInfoEntity laterWindowButOlderPickup = pickup(5, true,
                new LocalDateTime(2026, 10, 12, 10, 0));
        DispensationInfoEntity notDispensed = pickup(7, false,
                new LocalDateTime(2026, 10, 25, 8, 0));
        DispensationInfoStore store = new DispensationInfoStore(
                Arrays.asList(laterWindowButOlderPickup, notDispensed, latestActual));
        assertSame(latestActual, store.lastPickedUp());
    }

    @Test
    public void missingActualPickupTimestampMustNotBePresentedAsARealPickup() {
        DispensationInfoEntity missingTimestamp = pickup(8, true, null);
        DispensationInfoStore onlyMissing = new DispensationInfoStore(
                Collections.singletonList(missingTimestamp));
        assertNull(onlyMissing.lastPickedUp());

        DispensationInfoEntity known = pickup(4, true,
                new LocalDateTime(2026, 10, 15, 11, 0));
        DispensationInfoStore mixed = new DispensationInfoStore(
                Arrays.asList(missingTimestamp, known));
        assertSame(known, mixed.lastPickedUp());
    }

    @Test
    public void noPickupIsNotReportedWhenAllEntriesAreUndispensed() {
        DispensationInfoStore empty = new DispensationInfoStore(Collections.emptyList());
        assertNull(empty.lastPickedUp());
        DispensationInfoStore undispensed = new DispensationInfoStore(
                Collections.singletonList(pickup(3, false, null)));
        assertNull(undispensed.lastPickedUp());
    }
}
