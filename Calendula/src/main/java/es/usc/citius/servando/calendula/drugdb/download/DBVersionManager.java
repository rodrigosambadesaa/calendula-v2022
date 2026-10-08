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

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import es.usc.citius.servando.calendula.BuildConfig;
import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.database.DatabaseHelper;
import es.usc.citius.servando.calendula.util.HttpDownloadUtil;
import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;


public class DBVersionManager {

    private static final String VERSION_FILE = "versions.json";
    private static final String TAG = "DBVersionManager";

    /**
     * Connects to the database server, retrieves version info and determines the newest working
     * version for a given database.
     *
     * @param databaseID the database ID
     * @return the newest working version
     */
    public static String getLastDBVersion(Context ctx, String databaseID) {
        if (!LegacyRemoteArchivePolicy.permitsRemoteSqlInstallation()) {
            LogUtil.w(TAG, "Retired unsigned medicine archive backend disabled");
            return null;
        }
        final String downloadUrl = BuildConfig.DB_DOWNLOAD_URL;
        final String url = downloadUrl + VERSION_FILE;

        try {

            final String downloaded = HttpDownloadUtil.downloadFileToText(ctx, url);
            if (downloaded == null) {
                LogUtil.w(TAG, "getLastDBVersion: backend is not reachable");
                return null;
            }
            final String result = downloaded.trim();
            Type type = new TypeToken<Map<String, Map<Integer, String>>>() {
            }.getType();
            Map<String, Map<Integer, String>> versions = new Gson().fromJson(result, type);
            Map<Integer, String> dbVersions = versions == null ? null : versions.get(databaseID);
            String dbVersion = selectLastCompatibleVersion(
                    dbVersions, DatabaseHelper.DATABASE_VERSION);
            if (dbVersion == null) {
                LogUtil.w(TAG, "getLastDBVersion: missing or invalid compatible database version");
            }
            return dbVersion;

        } catch (Exception e) {
            LogUtil.e(TAG, "getLastDBVersion: ", e);
            return null;
        }
    }


    /**
     * Selects the most recent schema-compatible database archive from a remote manifest.
     * Archive versions become URL path components, so accept only real basic ISO dates
     * (yyyyMMdd) and reject malformed values instead of constructing arbitrary URLs.
     */
    static String selectLastCompatibleVersion(
            Map<Integer, String> versions, int databaseSchemaVersion) {
        if (versions == null || versions.isEmpty() || versions.containsKey(null)) {
            return null;
        }
        List<Integer> thresholds = new ArrayList<>(versions.keySet());
        Collections.sort(thresholds);

        int lastValid = -1;
        for (Integer threshold : thresholds) {
            if (threshold <= databaseSchemaVersion) {
                lastValid = threshold;
            } else {
                break;
            }
        }
        return lastValid == -1 ? null : validDatabaseVersionOrNull(versions.get(lastValid));
    }

    static boolean isValidDatabaseVersion(String version) {
        return validDatabaseVersionOrNull(version) != null;
    }

    private static String validDatabaseVersionOrNull(String version) {
        if (version == null || !version.matches("[0-9]{8}")) {
            return null;
        }
        try {
            ISODateTimeFormat.basicDate().parseLocalDate(version);
            return version;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Backwards-compatible overload for existing callers. */
    @Deprecated
    public static String getLastDBVersion(String databaseID) {
        Context context = CalendulaApp.getContext();
        return context != null ? getLastDBVersion(context, databaseID) : null;
    }


    /**
     * Checks if there is any available update for the current medicine database.
     *
     * @param ctx the context
     * @return the version code of the update if there is one available, <code>null</code> otherwise
     */
    public static String checkForUpdate(Context ctx) {
        final SharedPreferences prefs = PreferenceUtils.instance().preferences();
        final String noneId = ctx.getString(R.string.database_none_id);
        final String database = prefs.getString(PreferenceKeys.DRUGDB_CURRENT_DB.key(), noneId);
        final String currentVersion = prefs.getString(PreferenceKeys.DRUGDB_VERSION.key(), null);

        if (!database.equals(noneId) && !database.equals(ctx.getString(R.string.database_setting_up))) {
            if (currentVersion != null) {
                if (!isValidDatabaseVersion(currentVersion)) {
                    LogUtil.w(TAG, "checkForUpdate: invalid locally stored database version");
                    return null;
                }
                final String lastDBVersion = DBVersionManager.getLastDBVersion(ctx, database);
                if (lastDBVersion == null) {
                    LogUtil.w(TAG, "checkForUpdate: unable to reach database backend");
                    return null;
                }
                final DateTime lastDBDate = DateTime.parse(lastDBVersion, ISODateTimeFormat.basicDate());
                final DateTime currentDBDate = DateTime.parse(currentVersion, ISODateTimeFormat.basicDate());

                if (lastDBDate.isAfter(currentDBDate)) {
                    LogUtil.d(TAG, "checkForUpdate: Update found for database " + database + " (" + lastDBVersion + ")");
                    return lastDBVersion;
                } else {
                    LogUtil.d(TAG, "checkForUpdate: Database is updated. ID is '" + database + "', version is '" + currentVersion + "'");
                    return null;
                }
            } else {
                LogUtil.w(TAG, "checkForUpdate: Database is " + database + " but no version is set!");
                return null;
            }
        } else {
            LogUtil.d(TAG, "checkForUpdate: No database. No version check needed.");
            return null;
        }

    }
}
