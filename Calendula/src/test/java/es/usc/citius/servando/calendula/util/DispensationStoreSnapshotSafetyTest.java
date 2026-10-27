package es.usc.citius.servando.calendula.util;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import es.usc.citius.servando.calendula.healthcareprovider.persistence.DispensationInfoEntity;

import static org.junit.Assert.*;

/** Synthetic, read-only provider pickup windows: no patient records or SQLite. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DispensationStoreSnapshotSafetyTest {
    private static DispensationInfoEntity window(int day) {
        DispensationInfoEntity entry = new DispensationInfoEntity();
        entry.setDispenseInterval(new Interval(
                new DateTime(2026, 10, day, 9, 0),
                new DateTime(2026, 10, day + 1, 9, 0)));
        return entry;
    }

    @Test
    public void immutableInputIsNotReorderedByCalendarConstruction() {
        DispensationInfoEntity later = window(15);
        DispensationInfoEntity earlier = window(10);
        List<DispensationInfoEntity> original =
                Collections.unmodifiableList(Arrays.asList(later, earlier));
        DispensationInfoStore store = new DispensationInfoStore(original);

        assertSame("Incoming provider order must stay unchanged", later, original.get(0));
        assertSame(earlier, original.get(1));
        assertSame(earlier, store.pickups().get(0));
        assertSame(later, store.pickups().get(1));
    }

    @Test
    public void callerChangingListDoesNotRemoveAlreadyValidatedCalendarRows() {
        DispensationInfoEntity first = window(3);
        DispensationInfoEntity second = window(8);
        List<DispensationInfoEntity> original =
                new ArrayList<>(Arrays.asList(second, first));
        DispensationInfoStore store = new DispensationInfoStore(original);
        original.clear();

        assertEquals(2, store.pickups().size());
        assertSame(first, store.pickups().get(0));
        assertSame(second, store.pickups().get(1));
    }

    @Test
    public void missingProviderIntervalsFailClosedRatherThanPartiallyRecommending() {
        DispensationInfoEntity corrupt = new DispensationInfoEntity();
        for (List<DispensationInfoEntity> invalid : Arrays.asList(
                Arrays.asList(window(4), corrupt),
                Arrays.asList(window(4), null))) {
            try {
                new DispensationInfoStore(invalid);
                fail("A missing interval or row must reject the entire pickup snapshot");
            } catch (IllegalArgumentException expected) {
                // The UI query handler can now fail closed on a known error.
            }
        }
        try {
            new DispensationInfoStore(null);
            fail("A missing provider response must not become a valid empty snapshot");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
        assertTrue(new DispensationInfoStore(Collections.emptyList()).pickups().isEmpty());
    }
}
