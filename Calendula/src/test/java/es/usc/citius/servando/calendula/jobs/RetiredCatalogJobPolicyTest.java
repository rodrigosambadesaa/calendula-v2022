/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.jobs;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.evernote.android.job.JobManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.drugdb.download.DBVersionManager;
import es.usc.citius.servando.calendula.drugdb.download.LegacyRemoteArchivePolicy;

import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** No real downloads, SQL imports, or private patient data. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class RetiredCatalogJobPolicyTest {

    @Test
    public void obsoleteWeeklyCatalogJobIsCancelledNotScheduled() {
        assertFalse(LegacyRemoteArchivePolicy.permitsRemoteSqlInstallation());
        JobManager jobs = mock(JobManager.class);
        try (MockedStatic<JobManager> manager = mockStatic(JobManager.class)) {
            manager.when(JobManager::instance).thenReturn(jobs);
            CalendulaJobScheduler.scheduleJob(new CheckDatabaseUpdatesJob());
            verify(jobs).cancelAllForTag("CheckDatabaseUpdJob");
        }
    }

    @Test
    public void disabledCatalogNeverChecksRemoteVersionsOrPromptsUser() {
        assertFalse(LegacyRemoteArchivePolicy.permitsRemoteSqlInstallation());
        Context context = ApplicationProvider.getApplicationContext();
        try (MockedStatic<DBVersionManager> version = mockStatic(DBVersionManager.class)) {
            assertFalse(new CheckDatabaseUpdatesJob().checkForUpdate(context));
            version.verifyNoInteractions();
        }
    }
}
