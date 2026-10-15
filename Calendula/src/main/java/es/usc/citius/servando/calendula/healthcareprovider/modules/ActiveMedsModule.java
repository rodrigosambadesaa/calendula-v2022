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
import android.text.TextUtils;

import org.greenrobot.eventbus.Subscribe;
import org.joda.time.DateTime;
import org.joda.time.Duration;

import java.util.List;

import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.drugdb.model.persistence.HomogeneousGroup;
import es.usc.citius.servando.calendula.events.PersistenceEvents;
import es.usc.citius.servando.calendula.login.LoginStateManager;
import es.usc.citius.servando.calendula.modules.CalendulaModule;
import es.usc.citius.servando.calendula.healthcareprovider.jobs.UpdateMedicationFromServiceJob;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedEntity;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedType;
import es.usc.citius.servando.calendula.healthcareprovider.util.DBUtil;
import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

/**
 * * Makes sure that the periodic update background job is scheduled
 * * Tries to update once on application opened
 */
public class ActiveMedsModule extends CalendulaModule {

    public static final String ID = "CALENDULA_ACTIVE_MEDS_MODULE";

    private static final String TAG = "ActiveMedsModule";

    private static final Duration MIN_UPDATE_INTERVAL = Duration.standardMinutes(30);

    @Override
    public String getId() {
        return ID;
    }

    @Override
    protected void onApplicationStartup(Context ctx) {
        CalendulaApp.eventBus().register(this);
        if (DBUtil.isValidDB() && LoginStateManager.getInstance().isLoggedIn() && DB.patients().count() != 0) {
            // schedule unique periodic job if it is not already scheduled.
            if (!UpdateMedicationFromServiceJob.scheduleUniquePeriodic()) {
                // run the job once, if it's not been run in some time
                updateMedication();
            }
        }
    }

    @Subscribe
    public void handleDatabaseUpdateEvent(final PersistenceEvents.DatabaseUpdateEvent event) {
        List<ActiveMedEntity> storedActiveMedList = DB.healthcareProviderDB().activeMeds().findAll();
        for (ActiveMedEntity entity :
                storedActiveMedList) {
            if (entity.getType()== ActiveMedType.NATIONAL_CODE) {
                entity.setPrescription(DB.drugDB().prescriptions().findByCn(entity.getCode()));
                entity.setHomogeneousGroup(null);
            }
            else if (entity.getType()== ActiveMedType.DCPF) {
                entity.setHomogeneousGroup(DB.drugDB().homogeneousGroups().findOneBy(HomogeneousGroup.COLUMN_HOMOGENEOUS_GROUP_ID, entity.getCode()));
                entity.setPrescription(null);
            }
            DB.healthcareProviderDB().activeMeds().save(entity);
        }
    }

    /**
     * A restored/legacy timestamp must never suppress official treatment
     * refreshes indefinitely. Future dates (for example after a wall clock
     * rollback), missing dates and malformed values all require a retry.
     * The normal 30-minute throttling remains unchanged.
     */
    static boolean shouldScheduleOneShot(String savedDate, DateTime now) {
        if (TextUtils.isEmpty(savedDate)) {
            return true;
        }
        try {
            DateTime lastSuccess = DateTime.parse(savedDate);
            return lastSuccess.isAfter(now)
                    || new Duration(lastSuccess, now).isLongerThan(MIN_UPDATE_INTERVAL);
        } catch (IllegalArgumentException invalidDate) {
            return true;
        }
    }

    private void updateMedication() {
        final String lastGoodUpdate;
        try {
            lastGoodUpdate = PreferenceUtils.getString(
                    PreferenceKeys.REMOTE_LAST_GOOD_UPDATE_DATE, "");
        } catch (ClassCastException restoredWrongType) {
            // The last successful sync is unknown. Request a safe refresh,
            // without logging prior medical timestamps or account details.
            UpdateMedicationFromServiceJob.scheduleOneShot(false);
            return;
        }
        if (shouldScheduleOneShot(lastGoodUpdate, DateTime.now())) {
            UpdateMedicationFromServiceJob.scheduleOneShot(false);
        } else {
            LogUtil.d(TAG, "Official medication sync is recent enough");
        }
    }
}
