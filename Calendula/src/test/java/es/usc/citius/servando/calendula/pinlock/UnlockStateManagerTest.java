/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.pinlock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** JVM policy tests: no device time or wall-clock manipulation required. */
public class UnlockStateManagerTest {
    @Test
    public void acceptsOnlyPinSettingsThatActuallyExistInThePrivacySelector() {
        assertEquals(30, UnlockStateManager.parseAllowedTimeoutSeconds("30"));
        assertEquals(60, UnlockStateManager.parseAllowedTimeoutSeconds("60"));
        assertEquals(120, UnlockStateManager.parseAllowedTimeoutSeconds("120"));
        assertEquals(300, UnlockStateManager.parseAllowedTimeoutSeconds(" 300 "));
        for (String malformed : new String[]{null, "", "0", "-1", "301",
                "2147483647", "999999999999999", "NaN", "Infinity", "120000"}) {
            assertEquals("Tampered PIN timeout must deny access",
                    -1, UnlockStateManager.parseAllowedTimeoutSeconds(malformed));
        }
    }

    @Test
    public void monotonicExpiryIsInclusiveAtBoundaryAndDeniesAfterwards() {
        assertTrue(UnlockStateManager.withinUnlockWindow(10000L, 10000L, 30));
        assertTrue(UnlockStateManager.withinUnlockWindow(10000L, 40000L, 30));
        assertFalse(UnlockStateManager.withinUnlockWindow(10000L, 40001L, 30));
        assertTrue(UnlockStateManager.withinUnlockWindow(10000L, 310000L, 300));
        assertFalse(UnlockStateManager.withinUnlockWindow(10000L, 310001L, 300));
    }

    @Test
    public void staleFutureTimestampInvalidDurationOrRebootAlwaysDenies() {
        assertFalse(UnlockStateManager.withinUnlockWindow(-1L, 10000L, 120));
        assertFalse(UnlockStateManager.withinUnlockWindow(10000L, 9999L, 120));
        assertFalse(UnlockStateManager.withinUnlockWindow(10000L, 10000L, -1));
        assertFalse(UnlockStateManager.withinUnlockWindow(10000L, 10000L, 0));
        assertFalse(UnlockStateManager.withinUnlockWindow(10000L, 10000L, 301));
        assertFalse(UnlockStateManager.withinUnlockWindow(10000L, Long.MAX_VALUE, 120));
    }
}
