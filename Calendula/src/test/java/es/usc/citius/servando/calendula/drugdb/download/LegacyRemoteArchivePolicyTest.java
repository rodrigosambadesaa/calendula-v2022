/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.drugdb.download;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.util.HttpDownloadUtil;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;

import org.mockito.MockedStatic;

/**
 * No HTTP requests, disk writes or patient data. An unsigned remote SQL
 * archive must not be fetched even when the configured endpoint is set.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class LegacyRemoteArchivePolicyTest {

    @Test
    public void remoteSqlArchiveInstallationRemainsDisabled() {
        assertFalse("An authenticated source and signature verifier are absent",
                LegacyRemoteArchivePolicy.permitsRemoteSqlInstallation());
    }

    @Test
    public void oldManifestIsNeverRequestedBeforeSourceAuthentication() {
        Context context = ApplicationProvider.getApplicationContext();
        try (MockedStatic<HttpDownloadUtil> transport = mockStatic(HttpDownloadUtil.class)) {
            assertNull(DBVersionManager.getLastDBVersion(context, "AEMPS"));
            transport.verifyNoInteractions();
        }
    }
}
