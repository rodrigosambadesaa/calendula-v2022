package es.usc.citius.servando.calendula.login;

import org.junit.Test;
import java.util.concurrent.CountDownLatch;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Local callback wait policy; no Firebase network or credentials are used. */
public class InstanceIdTimeoutPolicyTest {
    @Test
    public void completedFirebaseCallbackDoesNotWait() {
        CountDownLatch completion = new CountDownLatch(1);
        completion.countDown();
        assertTrue(InstanceIDHelper.awaitInstanceId(completion, 0));
    }

    @Test
    public void missingFirebaseCallbackCanTimeOut() {
        CountDownLatch completion = new CountDownLatch(1);
        assertFalse(InstanceIDHelper.awaitInstanceId(completion, 0));
    }

    @Test
    public void interruptedFirebaseWaitRestoresCancellation() {
        CountDownLatch completion = new CountDownLatch(1);
        Thread.currentThread().interrupt();
        try {
            assertFalse(InstanceIDHelper.awaitInstanceId(completion, 15));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
