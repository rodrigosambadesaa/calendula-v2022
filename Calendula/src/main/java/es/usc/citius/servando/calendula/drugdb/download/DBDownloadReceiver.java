/*
 *    Calendula - An assistant for personal medication management.
 *    Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 *    Calendula is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this software.  If not, see <http://www.gnu.org/licenses/>.
 */

package es.usc.citius.servando.calendula.drugdb.download;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Pair;

import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

/**
 * Receives DownloadManager completion broadcasts for the database download
 * currently tracked by Calendula.
 */
public class DBDownloadReceiver extends BroadcastReceiver {

    private static final String TAG = "DBDownloadReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
            LogUtil.w(TAG, "Ignoring unexpected download broadcast");
            return;
        }

        final long completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
        if (completedId == -1L) {
            LogUtil.w(TAG, "Ignoring download completion without a valid download id");
            return;
        }

        final SharedPreferences preferences = PreferenceUtils.instance().preferences();
        final long trackedId = preferences.getLong(PreferenceKeys.DRUGDB_DOWNLOAD_ID.key(), -1L);

        // DownloadManager may deliver completion broadcasts unrelated to the database
        // currently tracked by Calendula. Never discard our state for a different id.
        if (trackedId == -1L || completedId != trackedId) {
            LogUtil.d(TAG, "Ignoring unrelated download completion: " + completedId);
            return;
        }

        final String downloadDb = preferences.getString(PreferenceKeys.DRUGDB_DOWNLOAD_DB.key(), null);
        final String dbVersion = preferences.getString(PreferenceKeys.DRUGDB_DOWNLOAD_VERSION.key(), null);
        final String type = preferences.getString(PreferenceKeys.DRUGDB_DOWNLOAD_TYPE.key(), null);

        try {
            if (downloadDb == null || dbVersion == null || type == null) {
                LogUtil.w(TAG, "Tracked database download is missing metadata");
                DownloadDatabaseHelper.instance().onDownloadFailed(context);
                return;
            }

            final Pair<Integer, String> status =
                    DownloadDatabaseHelper.instance().downloadStatus(completedId, context);

            if (status == null || status.first != DownloadManager.STATUS_SUCCESSFUL || status.second == null) {
                LogUtil.d(TAG, "Database download failed or has no local path: " + completedId);
                DownloadDatabaseHelper.instance().onDownloadFailed(context);
                return;
            }

            final DBInstallType dbInstallType;
            try {
                dbInstallType = DBInstallType.valueOf(type);
            } catch (IllegalArgumentException e) {
                LogUtil.e(TAG, "Invalid database install type: " + type, e);
                DownloadDatabaseHelper.instance().onDownloadFailed(context);
                return;
            }

            final androidx.core.util.Pair<String, String> databaseInfo =
                    new androidx.core.util.Pair<>(downloadDb, dbVersion);

            LogUtil.d(TAG, "Valid database download completed: " + completedId);
            InstallDatabaseService.startSetup(
                    context,
                    status.second,
                    databaseInfo,
                    dbInstallType);
        } finally {
            clearTrackedDownload(preferences);
        }
    }

    private static void clearTrackedDownload(SharedPreferences preferences) {
        preferences.edit()
                .remove(PreferenceKeys.DRUGDB_DOWNLOAD_ID.key())
                .remove(PreferenceKeys.DRUGDB_DOWNLOAD_DB.key())
                .remove(PreferenceKeys.DRUGDB_DOWNLOAD_VERSION.key())
                .remove(PreferenceKeys.DRUGDB_DOWNLOAD_TYPE.key())
                .apply();
    }
}
