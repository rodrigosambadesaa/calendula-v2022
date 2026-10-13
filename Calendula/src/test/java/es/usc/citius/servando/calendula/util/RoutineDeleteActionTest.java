/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util;

import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class RoutineDeleteActionTest {
    @Test
    public void failedPersistenceKeepsActionAvailableForRetry() {
        AtomicInteger attempts = new AtomicInteger();
        assertFalse(RoutineDeleteAction.run(() -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("Synthetic confidential prescription metadata");
        }));
        assertEquals(1, attempts.get());

        assertTrue("A second deletion may succeed after transient SQLite failure",
                RoutineDeleteAction.run(attempts::incrementAndGet));
        assertEquals(2, attempts.get());
    }

    @Test
    public void onlySuccessfulDeletionReturnsTrue() {
        assertFalse("Missing callback never implies deleted data",
                RoutineDeleteAction.run(null));
        assertTrue(RoutineDeleteAction.run(() -> { }));
    }
}
