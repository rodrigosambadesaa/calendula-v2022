/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.healthcareprovider.jobs;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Pure JVM wait policy; never creates tokens, starts jobs or calls the network. */
public class TokenRefreshWaitPolicyTest {
    @Test
    public void successfullyCompletedRefreshIsImmediatelyRecognized() {
        CountDownLatch completed = new CountDownLatch(1);
        completed.countDown();
        assertTrue(UpdateMedicationFromServiceJob.awaitTokenRefresh(completed, 0));
    }

    @Test
    public void stalledProviderCannotBlockForever() {
        CountDownLatch stalled = new CountDownLatch(1);
        assertFalse(UpdateMedicationFromServiceJob.awaitTokenRefresh(stalled, 0));
        assertTrue("Timeout must not mark a refresh complete",
                stalled.getCount() == 1L);
    }

    @Test
    public void cancellationRestoresWorkerInterruptStatus() {
        CountDownLatch pending = new CountDownLatch(1);
        Thread.currentThread().interrupt();
        try {
            assertFalse(UpdateMedicationFromServiceJob.awaitTokenRefresh(pending, 90));
            assertTrue("Job cancellation must not lose interrupt state",
                    Thread.currentThread().isInterrupted());
        } finally {
            // Do not leak the intentionally injected interrupt to JUnit.
            Thread.interrupted();
        }
    }
}
