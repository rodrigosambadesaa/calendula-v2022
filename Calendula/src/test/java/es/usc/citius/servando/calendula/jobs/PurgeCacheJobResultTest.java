/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.jobs;

import com.evernote.android.job.Job;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

/**
 * Cache job status is computed without opening patient records or SQLite.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PurgeCacheJobResultTest {

    @Test
    public void reportsSqliteCleanupFailureRatherThanSuccess() {
        assertEquals(Job.Result.FAILURE, PurgeCacheJob.statusForPurgeResult(-1));
        assertEquals(Job.Result.FAILURE, PurgeCacheJob.statusForPurgeResult(null));
    }

    @Test
    public void emptyAndSuccessfulCleanupsStillSucceed() {
        assertEquals(Job.Result.SUCCESS, PurgeCacheJob.statusForPurgeResult(0));
        assertEquals(Job.Result.SUCCESS, PurgeCacheJob.statusForPurgeResult(1));
        assertEquals(Job.Result.SUCCESS, PurgeCacheJob.statusForPurgeResult(100));
    }
}
