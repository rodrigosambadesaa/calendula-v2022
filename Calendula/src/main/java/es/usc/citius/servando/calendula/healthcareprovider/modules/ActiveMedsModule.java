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

    private void updateMedication() {
        final String lastGoodUpdate = PreferenceUtils.getString(PreferenceKeys.REMOTE_LAST_GOOD_UPDATE_DATE, "");
        try {
            if (!TextUtils.isEmpty(lastGoodUpdate)) {
                final DateTime lastGoodUpdateTime = DateTime.parse(lastGoodUpdate);
                Duration diff = new Duration(lastGoodUpdateTime, DateTime.now());
                if (diff.isLongerThan(MIN_UPDATE_INTERVAL)) {
                    UpdateMedicationFromServiceJob.scheduleOneShot(false);
                } else {
                    LogUtil.d(TAG, "updateMedication: update date is recent enough to not update again yet");
                }
            } else {
                UpdateMedicationFromServiceJob.scheduleOneShot(false);
            }
        } catch (Exception e) {
            LogUtil.e(TAG, "updateMedication: ", e);
            UpdateMedicationFromServiceJob.scheduleOneShot(false);
        }
    }
}
