/*
 *    Calendula - An assistant for personal medication management.
 *    Copyright (C) 2016 CITIUS - USC
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
 *    along with this software.  If not, see <http://www.gnu.org/licenses>.
 */

package es.usc.citius.servando.calendula.healthcareprovider.modules;

import android.content.Context;

import androidx.core.util.Pair;

import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;

import es.usc.citius.servando.calendula.BuildConfig;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.drugdb.download.DBInstallType;
import es.usc.citius.servando.calendula.drugdb.download.InstallDatabaseService;
import es.usc.citius.servando.calendula.modules.CalendulaModule;
import es.usc.citius.servando.calendula.healthcareprovider.util.DBUtil;
import es.usc.citius.servando.calendula.util.FileUtils;
import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

/**
 * Sets the database up in the first startup of the app
 */
public class SetupDatabaseModule extends CalendulaModule {

    public static final String ID = "CALENDULA_SETUP_DB_MODULE";

    private static final String TAG = "SetupDatabaseModule";

    private static final String FILENAME_PATTERN = "%1$s-%2$s.db";


    @Override
    public String getId() {
        return ID;
    }

    @Override
    protected void onApplicationStartup(Context ctx) {
        final String database = BuildConfig.BUNDLED_DB_VERSION;
        if (!DBUtil.isValidDB()) {
            installDrugDatabase(ctx, DBInstallType.SETUP);
        } else if (checkForUpdate(ctx) != null) {
            installDrugDatabase(ctx, DBInstallType.UPDATE);
        }
    }


    private void installDrugDatabase(Context ctx, DBInstallType installType) {
        final String database = BuildConfig.BUNDLED_DB_NAME;
        final String version = BuildConfig.BUNDLED_DB_VERSION;
        final String filename = String.format(FILENAME_PATTERN, database, version);
        final String assetsPath = "dbs/" + filename;
        try {
            String path = FileUtils.getCacheFileFromAssets(ctx, assetsPath, filename).getAbsolutePath();
            Pair<String, String> databaseInfo = new Pair<>(database, version);
            InstallDatabaseService.startSetup(ctx, path, databaseInfo, installType, true);
        } catch (Exception e) {
            // app can't continue without db, so crash
            LogUtil.e(TAG, "installDrugDatabase: Error installing drug db!");
            throw new RuntimeException(e);
        }
    }

    /**
     * Decide whether a complete, known catalog has a newer bundled version.
     * Missing/corrupt legacy preferences must not crash startup or trigger
     * a destructive catalog reinstall from an untrusted version string.
     */
    static boolean shouldUpdateCatalog(String selectedDatabase, String installedVersion,
                                       String bundledDatabase, String bundledVersion,
                                       String setupPlaceholder) {
        if (selectedDatabase == null || installedVersion == null
                || bundledDatabase == null || bundledVersion == null
                || bundledDatabase.equals(setupPlaceholder)
                || !bundledDatabase.equals(selectedDatabase)) {
            return false;
        }
        try {
            DateTime bundledDate = DateTime.parse(bundledVersion, ISODateTimeFormat.basicDate());
            DateTime installedDate = DateTime.parse(installedVersion, ISODateTimeFormat.basicDate());
            return bundledDate.isAfter(installedDate);
        } catch (IllegalArgumentException malformedVersion) {
            // The previous valid catalog stays selected; do not claim a
            // migration can be performed from an unparseable date.
            return false;
        }
    }

    public static String checkForUpdate(Context ctx) {
        if (ctx == null) {
            return null;
        }
        final String currentDatabase = PreferenceUtils.getString(PreferenceKeys.DRUGDB_CURRENT_DB, null);
        final String currentVersion = PreferenceUtils.getString(PreferenceKeys.DRUGDB_VERSION, null);
        final String bundledDatabase = BuildConfig.BUNDLED_DB_NAME;
        final String bundledVersion = BuildConfig.BUNDLED_DB_VERSION;
        if (!shouldUpdateCatalog(currentDatabase, currentVersion,
                bundledDatabase, bundledVersion, ctx.getString(R.string.database_setting_up))) {
            // A missing selection or malformed old catalog date is not
            // evidence that any medical record can safely be migrated.
            return null;
        }
        LogUtil.d(TAG, "A newer bundled medication catalog is available");
        return bundledVersion;
    }}
}
