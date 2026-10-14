/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.healthcareprovider.jobs;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Accept one AppAuth callback, unblock its worker, and reject callbacks
 * delivered after a retryable timeout or cancelled job.
 */
final class TokenRefreshGate {
    private final CountDownLatch finished = new CountDownLatch(1);
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    void complete(Runnable completion) {
        if (completion == null || !accepting.compareAndSet(true, false)) {
            return;
        }
        try {
            completion.run();
        } finally {
            finished.countDown();
        }
    }

    boolean await(long maximum, TimeUnit unit) throws InterruptedException {
        if (maximum < 0L || unit == null) {
            throw new IllegalArgumentException("A nonnegative token timeout is required");
        }
        if (finished.await(maximum, unit)) {
            return true;
        }
        close();
        return false;
    }

    void close() {
        accepting.set(false);
    }
}
