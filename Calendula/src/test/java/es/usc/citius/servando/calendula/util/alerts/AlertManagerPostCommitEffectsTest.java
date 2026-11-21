/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util.alerts;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Synthetic failure injection for effects that run AFTER clinical SQLite
 * COMMIT. No patient records, alarms or provider credentials are accessed.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AlertManagerPostCommitEffectsTest {

    @Test
    public void highWarningCleanupFailureStillNotifiesBothObservers() {
        List<String> calls = new ArrayList<>();
        RuntimeException failure = new IllegalStateException("synthetic cleanup error");
        try {
            AlertManager.finishCommittedAlertCreation(true,
                    () -> {
                        calls.add("cleanup");
                        throw failure;
                    },
                    () -> calls.add("schedules"),
                    () -> calls.add("alerts"));
            fail("Post-commit failure must not be misreported as success");
        } catch (IllegalStateException expected) {
            assertSame(failure, expected.getCause());
            assertTrue(expected.getMessage().contains("committed"));
        }
        assertEquals(Arrays.asList("cleanup", "schedules", "alerts"), calls);
    }

    @Test
    public void highWarningScheduleObserverFailureStillNotifiesAlertObservers() {
        List<String> calls = new ArrayList<>();
        RuntimeException failure = new IllegalStateException("synthetic schedule observer");
        try {
            AlertManager.finishCommittedAlertCreation(true,
                    () -> calls.add("cleanup"),
                    () -> {
                        calls.add("schedules");
                        throw failure;
                    },
                    () -> calls.add("alerts"));
            fail("Post-commit observer failure must propagate");
        } catch (IllegalStateException expected) {
            assertSame(failure, expected.getCause());
        }
        assertEquals(Arrays.asList("cleanup", "schedules", "alerts"), calls);
    }

    @Test
    public void lowWarningNeverRunsHighSeverityPlatformEffects() {
        List<String> calls = new ArrayList<>();
        AlertManager.finishCommittedAlertCreation(false,
                () -> fail("LOW warning must not clean HIGH reminders"),
                () -> fail("LOW warning must not notify schedule change"),
                () -> calls.add("alerts"));
        assertEquals(Arrays.asList("alerts"), calls);
    }

    @Test
    public void alertObserverFailureIsReportedAfterCommittedLowWarning() {
        List<String> calls = new ArrayList<>();
        RuntimeException failure = new IllegalStateException("synthetic alert observer");
        try {
            AlertManager.finishCommittedAlertCreation(false,
                    () -> fail("LOW warning should not clean reminders"),
                    () -> fail("LOW warning should not update schedules"),
                    () -> {
                        calls.add("alerts");
                        throw failure;
                    });
            fail("Alert observer failure must remain visible to caller");
        } catch (IllegalStateException expected) {
            assertSame(failure, expected.getCause());
            assertTrue(expected.getMessage().contains("committed"));
        }
        assertEquals(Arrays.asList("alerts"), calls);
    }

    @Test
    public void healthyHighWarningInvokesEveryPostCommitActionOnce() {
        List<String> calls = new ArrayList<>();
        AlertManager.finishCommittedAlertCreation(true,
                () -> calls.add("cleanup"),
                () -> calls.add("schedules"),
                () -> calls.add("alerts"));
        assertEquals(Arrays.asList("cleanup", "schedules", "alerts"), calls);
    }
}
