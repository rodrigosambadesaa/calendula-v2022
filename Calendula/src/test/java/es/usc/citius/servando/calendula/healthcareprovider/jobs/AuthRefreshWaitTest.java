/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.healthcareprovider.jobs;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Deterministic policy tests; no OAuth, network, patient or SQLite access. */
public class AuthRefreshWaitTest {
    @Test
    public void completedCallbackImmediatelyUnblocksWorker() {
        CountDownLatch completed = new CountDownLatch(1);
        completed.countDown();
        assertTrue(AuthRefreshWait.await(completed, 0, TimeUnit.MILLISECONDS));
    }

    @Test
    public void missingCallbackNeverBlocksPastDeadline() {
        CountDownLatch neverCompletes = new CountDownLatch(1);
        assertFalse(AuthRefreshWait.await(neverCompletes, 0, TimeUnit.MILLISECONDS));
        assertFalse(AuthRefreshWait.await(neverCompletes, 1, TimeUnit.MILLISECONDS));
    }

    @Test
    public void invalidDeadlineFailsWithoutWaiting() {
        assertFalse(AuthRefreshWait.await(null, 0, TimeUnit.MILLISECONDS));
        assertFalse(AuthRefreshWait.await(new CountDownLatch(1), -1, TimeUnit.SECONDS));
        assertFalse(AuthRefreshWait.await(new CountDownLatch(1), 1, null));
    }

    @Test
    public void interruptedWorkerPreservesInterruptSignal() {
        CountDownLatch pending = new CountDownLatch(1);
        Thread.currentThread().interrupt();
        try {
            assertFalse(AuthRefreshWait.await(pending, 1, TimeUnit.SECONDS));
            assertTrue("Worker cancellation must not be swallowed",
                    Thread.currentThread().isInterrupted());
        } finally {
            // Clear the synthetic test interrupt for subsequent JUnit tests.
            Thread.interrupted();
        }
    }
}
