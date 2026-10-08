/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.drugdb.download;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DownloadDatabaseHelperTest {

    @Test
    public void failedNewDownloadPreservesLastValidDatabaseReference() {
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences(
                "failed-db-test", Context.MODE_PRIVATE);
        prefs.edit().clear().putString("current", "setting_up")
                .putString("last_valid", "AEMPS")
                .apply();

        DownloadDatabaseHelper.clearFailedSelection(prefs, "current", "none");

        assertEquals("none", prefs.getString("current", null));
        assertEquals("AEMPS", prefs.getString("last_valid", null));
    }

    @Test
    public void firstInstallFailureStillClearsCurrentSelection() {
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences(
                "failed-db-first-test", Context.MODE_PRIVATE);
        prefs.edit().clear().putString("current", "setting_up").apply();

        DownloadDatabaseHelper.clearFailedSelection(prefs, "current", "none");

        assertEquals("none", prefs.getString("current", null));
    }
}
