/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities.schedules;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Synthetic failures only; no patient database access or actual reminders. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ScheduleBuildSaveActionTest {
    @Test
    public void completedBackgroundWriteReportsSuccess() {
        final boolean[] ran = {false};
        assertTrue(ScheduleBuildActivity.runSaveAction(() -> ran[0] = true));
        assertTrue("Successful write callback must have run", ran[0]);
    }

    @Test
    public void failedBackgroundWriteNeverReportsSuccess() {
        final int[] attempts = {0};
        assertFalse("A SQL-like runtime failure must leave the editor in error state",
                ScheduleBuildActivity.runSaveAction(() -> {
                    attempts[0]++;
                    throw new IllegalStateException("synthetic SQLite write failure");
                }));
        assertEquals("A failing save callback is not retried behind the user's back",
                1, attempts[0]);
    }
}
