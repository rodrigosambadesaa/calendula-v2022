/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.healthcareprovider.jobs;

import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TokenRefreshGateTest {
    @Test
    public void callbackCompletesExactlyOnceAndUnblocksWorker() throws Exception {
        TokenRefreshGate gate = new TokenRefreshGate();
        AtomicInteger effects = new AtomicInteger();
        gate.complete(effects::incrementAndGet);
        gate.complete(effects::incrementAndGet);
        assertTrue(gate.await(0, TimeUnit.MILLISECONDS));
        assertEquals(1, effects.get());
    }

    @Test
    public void timeoutRejectsSubsequentCredentialCallbacks() throws Exception {
        TokenRefreshGate gate = new TokenRefreshGate();
        assertFalse(gate.await(0, TimeUnit.MILLISECONDS));
        AtomicInteger effects = new AtomicInteger();
        gate.complete(effects::incrementAndGet);
        assertEquals(0, effects.get());
        assertFalse(gate.await(0, TimeUnit.MILLISECONDS));
    }

    @Test
    public void cancellingWorkerRejectsLateCallbacks() {
        TokenRefreshGate gate = new TokenRefreshGate();
        gate.close();
        AtomicInteger effects = new AtomicInteger();
        gate.complete(effects::incrementAndGet);
        assertEquals(0, effects.get());
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeTimeoutIsRejected() throws Exception {
        new TokenRefreshGate().await(-1, TimeUnit.SECONDS);
    }
}
