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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.NetworkUtils;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

public class DownloadDatabaseHelper {

    private static final String TAG = "DownloadDatabaseHelper";

    private static DownloadDatabaseHelper instance;

    private DownloadDatabaseHelper() {
    }

    public static DownloadDatabaseHelper instance() {
        if (instance == null) {
            instance = new DownloadDatabaseHelper();
        }
        return instance;
    }

    public void showDownloadDialog(final Context dialogCtx, final String database,
                                   final DownloadDatabaseDialogCallback callback) {
        LogUtil.d(TAG, "showDownloadDialog() called for database " + database);
        final Context appContext = dialogCtx.getApplicationContext();
        AlertDialog.Builder builder = new AlertDialog.Builder(dialogCtx);
        builder.setTitle(R.string.download_db_dialog_title);
        builder.setCancelable(false);
        builder.setMessage(R.string.download_db_dialog_message)
                .setCancelable(false)
                .setPositiveButton(R.string.db_download_and_setup, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        if (NetworkUtils.isNetworkAvailable(appContext)) {
                            if (callback != null) {
                                callback.onDownloadAcceptedOrCancelled(true);
                            }
                            downloadDatabase(appContext, database, DBInstallType.SETUP);
                            dialog.dismiss();
                        } else {
                            if (callback != null) {
                                callback.onDownloadAcceptedOrCancelled(false);
                            }
                            Toast.makeText(appContext, R.string.message_no_internet_error,
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton(dialogCtx.getString(R.string.cancel),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                                if (callback != null) {
                                    callback.onDownloadAcceptedOrCancelled(false);
                                }
                            }
                        });
        builder.create().show();
    }

    public void onDownloadFailed(Context context) {
        onDownloadFailed(context, true);
    }

    public void onDownloadFailed(final Context context, boolean clearDatabaseSelection) {
        InstallDatabaseService.isRunning = false;
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context.getApplicationContext(),
                        R.string.download_db_unexpected_error,
                        Toast.LENGTH_LONG).show();
            }
        });

        if (clearDatabaseSelection) {
            SharedPreferences settings = PreferenceUtils.instance().preferences();
            settings.edit()
                    .putString(PreferenceKeys.DRUGDB_LAST_VALID.key(),
                            context.getString(R.string.database_none_id))
                    .putString(PreferenceKeys.DRUGDB_CURRENT_DB.key(),
                            context.getString(R.string.database_none_id))
                    .apply();
        }

        Intent bcIntent = new Intent(InstallDatabaseService.ACTION_ERROR);
        bcIntent.setPackage(context.getPackageName());
        context.sendBroadcast(bcIntent);
    }

    public boolean isDBDownloadingOrInstalling(Context context) {
        final boolean running = InstallDatabaseService.isRunning;
        LogUtil.d(TAG, "isDBDownloadingOrInstalling: " + running);
        return running;
    }

    void downloadDatabase(Context ctx, final String database, final DBInstallType type) {
        InstallDatabaseService.startDownloadAndSetup(ctx, database, type);
    }

    public interface DownloadDatabaseDialogCallback {
        void onDownloadAcceptedOrCancelled(boolean accepted);
    }
}
