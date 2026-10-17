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

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;

import es.usc.citius.servando.calendula.util.DrugCatalogSelection;
import es.usc.citius.servando.calendula.util.LogUtil;


public class UpdateDatabaseService extends IntentService {

    public static final String EXTRA_DATABASE_ID = "Calendula.UpdateDatabaseService.DATABASE_NAME";
    private static final String TAG = "UpdateDatabaseService";

    public UpdateDatabaseService() {
        super(TAG);
    }

    /**
     * A restored legacy update notification can carry a null, wrong-type or
     * unparcelable extra. Never stringify an Intent or log the raw catalog ID.
     * Reads only the requested ID; no network, preferences or DB side effects.
     */
    static String requestedCatalogOrNull(Intent intent) {
        if (intent == null) {
            return null;
        }
        try {
            String id = intent.getStringExtra(EXTRA_DATABASE_ID);
            return id == null || id.trim().isEmpty() ? null : id;
        } catch (RuntimeException malformedExtra) {
            return null;
        }
    }

    static boolean isSameSelectedCatalog(String requested, String selected) {
        return requested != null && selected != null
                && !requested.trim().isEmpty() && requested.equals(selected);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        // The saved catalog preference may have an incompatible restored type.
        // Never authorize an update using fallback data from another catalog.
        final String database = requestedCatalogOrNull(intent);
        final String selected = DrugCatalogSelection.selectedOrNull();
        if (!isSameSelectedCatalog(database, selected)) {
            LogUtil.w(TAG, "Skipping missing, malformed or outdated database update request");
            return;
        }
        final Context ctx = getApplicationContext();
        if (DBVersionManager.checkForUpdate(ctx) != null) {
            DownloadDatabaseHelper.instance().downloadDatabase(ctx, database, DBInstallType.UPDATE);
        } else {
            LogUtil.d(TAG, "No eligible catalog update available");
        }
    }

}
