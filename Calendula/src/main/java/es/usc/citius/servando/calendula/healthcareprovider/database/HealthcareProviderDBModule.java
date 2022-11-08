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

package es.usc.citius.servando.calendula.healthcareprovider.database;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.database.DatabaseHelper;
import es.usc.citius.servando.calendula.util.LogUtil;


public class HealthcareProviderDBModule {


    private static final String TAG = "DrugDBModule";
    private static HealthcareProviderDBModule instance = null;

    private final ActiveMedDao ActiveMeds;
    private final DosageDao Dosages;
    private final DosageEntryDao DosageEntries;
    private final DispensationInfoDao DispensationInfo;

    private HealthcareProviderDBModule(final DatabaseHelper db) {
        ActiveMeds = new ActiveMedDao(db);
        Dosages = new DosageDao(db);
        DosageEntries = new DosageEntryDao(db);
        DispensationInfo = new DispensationInfoDao(db);
        LogUtil.v(TAG, "Healthcare provider DB Module initialized");
    }

    public static HealthcareProviderDBModule getInstance() {
        if (instance == null) {
            instance = new HealthcareProviderDBModule(DB.helper());
        }
        return instance;
    }

    public ActiveMedDao activeMeds() {
        return ActiveMeds;
    }

    public DosageDao dosages() {
        return Dosages;
    }

    public DosageEntryDao dosageEntries() {
        return DosageEntries;
    }

    public DispensationInfoDao dispensationInfo() {
        return DispensationInfo;
    }
}
