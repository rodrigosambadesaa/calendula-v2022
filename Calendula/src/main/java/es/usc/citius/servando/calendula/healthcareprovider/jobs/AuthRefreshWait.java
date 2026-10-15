/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.healthcareprovider.jobs;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Never let an OAuth callback pin a medication sync or document-fetch worker
 * indefinitely. A timeout is retryable, not proof that the patient revoked
 * authorization.
 */
final class AuthRefreshWait {
    private static final long MAX_WAIT_SECONDS = 30L;

    private AuthRefreshWait() {
    }

    static boolean await(CountDownLatch finished) {
        return await(finished, MAX_WAIT_SECONDS, TimeUnit.SECONDS);
    }

    /** Package-visible deadline override for deterministic JVM tests. */
    static boolean await(CountDownLatch finished, long timeout, TimeUnit unit) {
        if (finished == null || timeout < 0L || unit == null) {
            return false;
        }
        try {
            return finished.await(timeout, unit);
        } catch (InterruptedException cancelled) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
