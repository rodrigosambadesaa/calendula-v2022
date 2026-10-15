/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.healthcareprovider.modules;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Timestamp policy only; does not connect, schedule or alter medication. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class OfficialMedicationRefreshTimestampTest {
    private static final DateTime NOW = DateTime.parse("2026-10-15T12:00:00Z");

    @Test
    public void recentSuccessfulUpdateStillAvoidsUnnecessaryRemoteTraffic() {
        assertFalse(ActiveMedsModule.shouldScheduleOneShot(
                NOW.minusMinutes(5).toString(), NOW));
        assertFalse(ActiveMedsModule.shouldScheduleOneShot(
                NOW.minusMinutes(30).toString(), NOW));
    }

    @Test
    public void oldTimestampTriggersOneShotRefresh() {
        assertTrue(ActiveMedsModule.shouldScheduleOneShot(
                NOW.minusMinutes(31).toString(), NOW));
        assertTrue(ActiveMedsModule.shouldScheduleOneShot(
                NOW.minusDays(14).toString(), NOW));
    }

    @Test
    public void futureTimestampAfterClockRollbackCannotSuppressUpdatesForever() {
        assertTrue(ActiveMedsModule.shouldScheduleOneShot(
                NOW.plusMinutes(1).toString(), NOW));
        assertTrue(ActiveMedsModule.shouldScheduleOneShot(
                NOW.plusYears(50).toString(), NOW));
    }

    @Test
    public void absentOrUnparseableBackupTimestampsTriggerSafeRefresh() {
        for (String invalid : new String[]{null, "", "bad timestamp",
                "99999999999999999999999", "2026-17-99"}) {
            assertTrue(ActiveMedsModule.shouldScheduleOneShot(invalid, NOW));
        }
    }
}
