/*
 *    Calendula - An assistant for personal medication management.
 *    Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 *    Calendula is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    Calendula is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this software.  If not, see <http://www.gnu.org/licenses/>.
 */

package es.usc.citius.servando.calendula.drugdb.download;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.util.Pair;

import com.mikepenz.google_material_typeface_library.GoogleMaterial;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import es.usc.citius.servando.calendula.BuildConfig;
import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.activities.MedicinesActivity;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.database.migrationHelpers.DrugModelMigrationHelper;
import es.usc.citius.servando.calendula.drugdb.DBRegistry;
import es.usc.citius.servando.calendula.drugdb.PrescriptionDBMgr;
import es.usc.citius.servando.calendula.drugdb.model.persistence.Prescription;
import es.usc.citius.servando.calendula.events.PersistenceEvents;
import es.usc.citius.servando.calendula.notifications.NotificationHelper;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.util.HttpDownloadUtil;
import es.usc.citius.servando.calendula.util.IconUtils;
import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

/**
 * Foreground service for prescription database download/setup/update work.
 *
 * Network, ZIP and SQLite work runs on a single background executor. Keeping the controlled
 * downloader inside the foreground service preserves background reliability while ensuring
 * every actual HTTP(S) destination, including redirects, passes the mandatory network preflight.
 */
public class InstallDatabaseService extends Service {

    public static final String ACTION_COMPLETE = "calendula.persistence.medDatabases.action.DONE";
    public static final String ACTION_ERROR = "calendula.persistence.medDatabases.action.ERROR";
    private static final String TAG = "InstallDatabaseService";
    private static final String ACTION_SETUP = "calendula.persistence.medDatabases.action.SETUP";
    private static final String ACTION_UPDATE = "calendula.persistence.medDatabases.action.UPDATE";
    private static final String ACTION_DOWNLOAD = "calendula.persistence.medDatabases.action.DOWNLOAD";
    private static final String EXTRA_DB_PATH = "calendula.persistence.medDatabases.extra.DB_PATH";
    private static final String EXTRA_DB_PREF_VALUE = "calendula.persistence.medDatabases.extra.DB_PREF_VALUE";
    private static final String EXTRA_DB_VERSION = "calendula.persistence.medDatabases.extra.DB_VERSION";
    private static final String EXTRA_DB_INSTALL_TYPE = "calendula.persistence.medDatabases.extra.DB_INSTALL_TYPE";
    private static final String EXTRA_SILENT = "calendula.persistence.medDatabases.extra.SILENT";
    private static final String DOWNLOAD_SUFFIX = ".db";
    public static int NOTIFICATION_ID = "InstallDatabaseService".hashCode();
    public static volatile boolean isRunning = false;

    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private NotificationCompat.Builder mBuilder;
    private NotificationManagerCompat mNotifyManager;
    private boolean silent = false;

    public static void startSetup(Context context, String dbPath,
                                  Pair<String, String> databaseInfo, DBInstallType type) {
        startSetup(context, dbPath, databaseInfo, type, false);
    }

    public static void startSetup(Context context, String dbPath,
                                  Pair<String, String> databaseInfo, DBInstallType type,
                                  final boolean silent) {
        context = context.getApplicationContext();
        Intent intent = new Intent(context, InstallDatabaseService.class);
        intent.setAction(type == DBInstallType.UPDATE ? ACTION_UPDATE : ACTION_SETUP);
        intent.putExtra(EXTRA_DB_PATH, dbPath);
        intent.putExtra(EXTRA_DB_PREF_VALUE, databaseInfo.first);
        intent.putExtra(EXTRA_DB_VERSION, databaseInfo.second);
        intent.putExtra(EXTRA_SILENT, silent);
        startServiceCompat(context, intent);
    }

    public static void startDownloadAndSetup(Context context, String database, DBInstallType type) {
        context = context.getApplicationContext();
        Intent intent = new Intent(context, InstallDatabaseService.class);
        intent.setAction(ACTION_DOWNLOAD);
        intent.putExtra(EXTRA_DB_PREF_VALUE, database);
        intent.putExtra(EXTRA_DB_INSTALL_TYPE, type.name());
        intent.putExtra(EXTRA_SILENT, false);
        startServiceCompat(context, intent);
    }

    private static void startServiceCompat(Context context, Intent intent) {
        LogUtil.d(TAG, "InstallDatabaseService: enqueuing database operation");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, final int startId) {
        if (intent == null) {
            LogUtil.w(TAG, "Ignoring null service intent");
            stopSelf(startId);
            return Service.START_NOT_STICKY;
        }

        final String action = intent.getAction();
        if (!ACTION_SETUP.equals(action)
                && !ACTION_UPDATE.equals(action)
                && !ACTION_DOWNLOAD.equals(action)) {
            LogUtil.w(TAG, "Ignoring unsupported database service action");
            stopSelf(startId);
            return Service.START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, getNotification(100, 0, null));
        isRunning = true;

        final String dbPath = intent.getStringExtra(EXTRA_DB_PATH);
        final String dbPref = intent.getStringExtra(EXTRA_DB_PREF_VALUE);
        final String dbVersion = intent.getStringExtra(EXTRA_DB_VERSION);
        final String installType = intent.getStringExtra(EXTRA_DB_INSTALL_TYPE);
        final boolean requestSilent = intent.getBooleanExtra(EXTRA_SILENT, false);

        databaseExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    silent = requestSilent;
                    final DBInstallType type;
                    final boolean success;

                    if (ACTION_DOWNLOAD.equals(action)) {
                        type = parseInstallType(installType);
                        success = type != null && handleDownloadAndSetup(dbPref, type);
                    } else {
                        type = ACTION_UPDATE.equals(action) ? DBInstallType.UPDATE : DBInstallType.SETUP;
                        success = handleSetup(dbPath, dbPref, dbVersion, type);
                    }

                    if (success) {
                        if (type == DBInstallType.UPDATE) {
                            checkForInvalidData();
                            CalendulaApp.eventBus().post(new PersistenceEvents.DatabaseUpdateEvent());
                        }
                        onComplete();
                    } else if (type == null) {
                        failDatabaseOperation("Invalid database install type", null, DBInstallType.SETUP);
                    }
                } finally {
                    stopSelf(startId);
                }
            }
        });

        return Service.START_NOT_STICKY;
    }

    private DBInstallType parseInstallType(String value) {
        if (value == null) {
            return null;
        }
        try {
            return DBInstallType.valueOf(value);
        } catch (IllegalArgumentException e) {
            LogUtil.e(TAG, "Invalid database install type", e);
            return null;
        }
    }

    private boolean handleDownloadAndSetup(final String database, final DBInstallType type) {
        File destination = null;
        try {
            final PrescriptionDBMgr mgr = DBRegistry.instance().db(database);
            if (mgr == null) {
                throw new IllegalArgumentException("Unknown prescription database");
            }

            final String dbName = mgr.id();
            final String dbVersion = DBVersionManager.getLastDBVersion(this, dbName);
            if (dbVersion == null) {
                throw new IOException("Unable to resolve database version backend");
            }

            final File downloads = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (downloads == null) {
                throw new IOException("External download directory is unavailable");
            }
            if (!downloads.exists() && !downloads.mkdirs()) {
                throw new IOException("Could not create database download directory");
            }

            destination = new File(downloads, dbName + DOWNLOAD_SUFFIX);
            if (destination.exists() && !destination.delete()) {
                throw new IOException("Could not replace previous database download");
            }

            final String url = getString(
                    R.string.database_file_location,
                    BuildConfig.DB_DOWNLOAD_URL,
                    dbName,
                    dbVersion);
            LogUtil.d(TAG, "Downloading database " + dbName + " version " + dbVersion);

            if (!HttpDownloadUtil.downloadFile(this, url, destination)) {
                throw new IOException("Database download failed");
            }

            final boolean setupSucceeded =
                    handleSetup(destination.getAbsolutePath(), dbName, dbVersion, type);
            if (!setupSucceeded && destination.exists() && !destination.delete()) {
                LogUtil.w(TAG, "Unable to remove failed database download");
            }
            return setupSucceeded;
        } catch (Exception e) {
            if (destination != null && destination.exists() && !destination.delete()) {
                LogUtil.w(TAG, "Unable to remove partial database download");
            }
            failDatabaseOperation("Database download/setup failed", e, type);
            return false;
        }
    }

    private boolean handleSetup(final String dbPath, final String dbPref,
                                final String dbVersion, final DBInstallType type) {
        try {
            if (dbPath == null || dbPref == null || dbVersion == null) {
                throw new IllegalArgumentException("Missing database setup metadata");
            }

            final PrescriptionDBMgr mgr = DBRegistry.instance().db(dbPref);
            if (mgr == null) {
                throw new IllegalArgumentException("Unknown prescription database");
            }

            mgr.setup(InstallDatabaseService.this, dbPath,
                    new PrescriptionDBMgr.SetupProgressListener() {
                        @Override
                        public void onProgressUpdate(int progress) {
                            LogUtil.d(TAG, "Setting up db " + progress + "%");
                            showNotification(100, progress);
                        }
                    });

            SharedPreferences settings = PreferenceUtils.instance().preferences();
            settings.edit()
                    .putString(PreferenceKeys.DRUGDB_LAST_VALID.key(), dbPref)
                    .putString(PreferenceKeys.DRUGDB_CURRENT_DB.key(), dbPref)
                    .putString(PreferenceKeys.DRUGDB_VERSION.key(), dbVersion)
                    .apply();

            try {
                DB.drugDB().prescriptions().executeRaw("VACUUM;");
            } catch (Exception e) {
                LogUtil.w(TAG, "Database installed but VACUUM failed");
            }

            LogUtil.d(TAG, dbPref + "-" + dbVersion + ": Finished saving "
                    + DB.drugDB().prescriptions().count() + " prescriptions");
            return true;
        } catch (Exception e) {
            failDatabaseOperation("Error while saving prescription data", e, type);
            return false;
        }
    }

    private void failDatabaseOperation(String message, Exception error, DBInstallType type) {
        if (error != null) {
            LogUtil.e(TAG, message, error);
        } else {
            LogUtil.e(TAG, message);
        }
        final boolean clearDatabaseSelection = type != DBInstallType.UPDATE;
        DownloadDatabaseHelper.instance().onDownloadFailed(this, clearDatabaseSelection);
        onFailure();
    }

    private void checkForInvalidData() {
        LogUtil.d(TAG, "checkForInvalidData() called");
        boolean anyMissing = false;
        for (Medicine m : DB.medicines().findAll()) {
            if (m.isBoundToPrescription()) {
                final String cn = m.getCn();
                final Prescription byCn = DB.drugDB().prescriptions().findByCn(cn);
                if (byCn == null) {
                    anyMissing = true;
                    m.setCn(null);
                }
            }
        }
        if (anyMissing) {
            notifyDataMissing();
        }
    }

    private void notifyDataMissing() {
        mBuilder = new NotificationCompat.Builder(this, NotificationHelper.CHANNEL_DEFAULT_ID)
                .setTicker("")
                .setSmallIcon(R.drawable.ic_launcher_white)
                .setLargeIcon(IconUtils.icon(getApplicationContext(),
                        GoogleMaterial.Icon.gmd_alert_triangle, R.color.white, 100).toBitmap())
                .setTicker(getString(R.string.text_database_update_data_lost))
                .setAutoCancel(true)
                .setContentTitle(getString(R.string.title_database_update_data_lost))
                .setContentText(getString(R.string.text_database_update_data_lost));
        getNotificationManager().notify(NOTIFICATION_ID, mBuilder.build());
    }

    private void showNotification(int max, int prog) {
        PendingIntent pIntent = null;
        if (!silent) {
            Intent activity = new Intent(this, MedicinesActivity.class);
            pIntent = PendingIntent.getActivity(this, 0, activity,
                    PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }
        final Notification notification = getNotification(max, prog, pIntent);
        getNotificationManager().notify(NOTIFICATION_ID, notification);
    }

    private Notification getNotification(int max, int prog, PendingIntent pIntent) {
        mBuilder = new NotificationCompat.Builder(this, NotificationHelper.CHANNEL_SETUP_ID)
                .setTicker("")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setTicker(getString(R.string.install_db_notification_ticker))
                .setAutoCancel(false)
                .setContentIntent(pIntent)
                .setContentTitle(getString(R.string.install_db_notification_title))
                .setContentText(getString(R.string.install_db_notification_content))
                .setProgress(max, prog, false);
        return mBuilder.build();
    }

    private void onComplete() {
        isRunning = false;
        stopForeground(true);
        if (!silent) {
            mBuilder.setContentTitle(getString(R.string.install_db_notification_oncomplete));
            mBuilder.setContentText(getString(R.string.install_db_notification_oncomplete_text));
            mBuilder.setAutoCancel(true);
            mBuilder.setProgress(100, 100, false);
            mBuilder.setSmallIcon(R.drawable.ic_done_white_36dp);
            mBuilder.setContentInfo("");
            getNotificationManager().notify(NOTIFICATION_ID, mBuilder.build());
        }
        Intent bcIntent = new Intent(ACTION_COMPLETE);
        bcIntent.setPackage(getPackageName());
        sendBroadcast(bcIntent);
        DrugModelMigrationHelper.linkMedsAfterUpdate();
    }

    private void onFailure() {
        isRunning = false;
        stopForeground(true);
        if (!silent) {
            if (mBuilder == null) {
                mBuilder = new NotificationCompat.Builder(this, NotificationHelper.CHANNEL_DEFAULT_ID);
            }
            mBuilder.setContentTitle(getString(R.string.install_db_notification_onfailure));
            mBuilder.setContentText(getString(R.string.install_db_notification_onfailure_content));
            mBuilder.setAutoCancel(true);
            mBuilder.setProgress(100, 100, false);
            mBuilder.setSmallIcon(R.drawable.ic_clear_search_holo_light);
            mBuilder.setContentInfo("");
            mBuilder.setContentIntent(null);
            getNotificationManager().notify(NOTIFICATION_ID, mBuilder.build());
        }
    }

    private NotificationManagerCompat getNotificationManager() {
        if (mNotifyManager == null) {
            mNotifyManager = NotificationManagerCompat.from(this);
        }
        return mNotifyManager;
    }

    @Override
    public void onDestroy() {
        databaseExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
