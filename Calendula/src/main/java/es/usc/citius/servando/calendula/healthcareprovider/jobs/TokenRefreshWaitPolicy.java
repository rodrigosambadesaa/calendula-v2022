/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.healthcareprovider.jobs;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Shared bounded wait for remote official-medicine token refresh callbacks. */
final class TokenRefreshWaitPolicy {
    static final long TIMEOUT_SECONDS = 90L;

    private TokenRefreshWaitPolicy() {
    }

    static boolean awaitCompletion(CountDownLatch completion, long timeoutSeconds) {
        try {
            return completion.await(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
