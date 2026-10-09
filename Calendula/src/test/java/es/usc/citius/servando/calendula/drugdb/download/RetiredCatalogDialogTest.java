/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.drugdb.download;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;

import java.util.concurrent.atomic.AtomicReference;

import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.util.NetworkUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mockStatic;

/**
 * A retired unauthenticated SQL catalog must never appear to be downloadable.
 * This is a UI-only test and starts no network work or background service.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class RetiredCatalogDialogTest {

    @Test
    public void retiredSourceShowsExplanatoryDialogWithoutStartingDownloads() {
        assertFalse(LegacyRemoteArchivePolicy.permitsRemoteSqlInstallation());
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        AtomicReference<Boolean> accepted = new AtomicReference<>();
        try (MockedStatic<NetworkUtils> network = mockStatic(NetworkUtils.class);
             MockedStatic<InstallDatabaseService> service = mockStatic(InstallDatabaseService.class)) {
            DownloadDatabaseHelper.instance().showDownloadDialog(
                    activity, "AEMPS", accepted::set);

            AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
            assertNotNull("The user must be told why the catalog is unavailable", dialog);
            assertTrue(dialog.isShowing());
            TextView message = dialog.findViewById(android.R.id.message);
            assertNotNull("The warning must contain an explanation", message);
            assertEquals(activity.getString(R.string.remote_catalog_unavailable_description),
                    message.getText().toString());

            assertNotNull(dialog.getButton(DialogInterface.BUTTON_POSITIVE));
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
            assertEquals("Must not silently choose or replace an existing catalog",
                    Boolean.FALSE, accepted.get());
            network.verifyNoInteractions();
            service.verifyNoInteractions();
        } finally {
            activity.finish();
        }
    }
}
