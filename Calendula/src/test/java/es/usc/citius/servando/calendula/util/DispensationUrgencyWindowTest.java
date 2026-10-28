package es.usc.citius.servando.calendula.util;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;

import es.usc.citius.servando.calendula.healthcareprovider.persistence.DispensationInfoEntity;

import static org.junit.Assert.*;

/** Synthetic provider windows only: never touches patient records or alarms. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DispensationUrgencyWindowTest {
    private static DispensationInfoEntity window(DateTime start, DateTime end) {
        DispensationInfoEntity pickup = new DispensationInfoEntity();
        pickup.setDispenseInterval(new Interval(start, end));
        return pickup;
    }

    @Test
    public void twoDayAuthorizedWindowIsUrgentWithoutWaitingSevenDays() {
        DateTime now = new DateTime(2026, 10, 28, 12, 0);
        DispensationInfoEntity pickup = window(now.minusHours(3), now.plusDays(2));
        assertTrue(DispensationInfoStore.isUrgentWithinProviderWindow(pickup, now, 3));
    }

    @Test
    public void tenDayWindowBecomesUrgentOnlyNearRealExpiry() {
        DateTime start = new DateTime(2026, 10, 20, 12, 0);
        DispensationInfoEntity pickup = window(start, start.plusDays(10));
        assertFalse(DispensationInfoStore.isUrgentWithinProviderWindow(
                pickup, start.plusDays(4), 3));
        assertTrue(DispensationInfoStore.isUrgentWithinProviderWindow(
                pickup, start.plusDays(7), 3));
        assertTrue(DispensationInfoStore.isUrgentWithinProviderWindow(
                pickup, start.plusDays(9), 3));
        assertFalse("Expired interval cannot imply an active pickup",
                DispensationInfoStore.isUrgentWithinProviderWindow(
                        pickup, start.plusDays(10), 3));
    }

    @Test
    public void neverShowUrgencyForFutureCompletedOrUnverifiedIntervals() {
        DateTime now = new DateTime(2026, 10, 28, 12, 0);
        DispensationInfoEntity future = window(now.plusMinutes(1), now.plusDays(2));
        assertFalse(DispensationInfoStore.isUrgentWithinProviderWindow(future, now, 3));
        DispensationInfoEntity current = window(now.minusDays(1), now.plusDays(1));
        current.setDispensed(true);
        assertFalse(DispensationInfoStore.isUrgentWithinProviderWindow(current, now, 3));
        assertFalse(DispensationInfoStore.isUrgentWithinProviderWindow(null, now, 3));
        assertFalse(DispensationInfoStore.isUrgentWithinProviderWindow(
                new DispensationInfoEntity(), now, 3));
        assertFalse(DispensationInfoStore.isUrgentWithinProviderWindow(current, null, 3));
        assertFalse(DispensationInfoStore.isUrgentWithinProviderWindow(current, now, 0));
    }

    @Test
    public void emptyProviderSnapshotHasNoUrgentPickups() {
        DispensationInfoStore empty = new DispensationInfoStore(Collections.emptyList());
        assertTrue(empty.urgentMeds().isEmpty());
    }
}
