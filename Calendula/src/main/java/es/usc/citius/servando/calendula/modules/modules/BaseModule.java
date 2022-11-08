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

package es.usc.citius.servando.calendula.modules.modules;

import android.content.Context;
import android.os.Build;

import com.evernote.android.job.JobManager;
import com.mikepenz.community_material_typeface_library.CommunityMaterial;
import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.mikepenz.iconics.Iconics;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.drugdb.DBRegistry;
import es.usc.citius.servando.calendula.jobs.CalendulaJob;
import es.usc.citius.servando.calendula.jobs.CalendulaJobCreator;
import es.usc.citius.servando.calendula.jobs.CalendulaJobScheduler;
import es.usc.citius.servando.calendula.jobs.PurgeCacheJob;
import es.usc.citius.servando.calendula.modules.CalendulaModule;
import es.usc.citius.servando.calendula.notifications.NotificationHelper;
import es.usc.citius.servando.calendula.scheduling.Agenda;
import es.usc.citius.servando.calendula.scheduling.MedicationIntakeReceiver;
import es.usc.citius.servando.calendula.scheduling.model.EventType;
import es.usc.citius.servando.calendula.util.PreferenceUtils;
import es.usc.citius.servando.calendula.util.PresentationsTypeface;
import es.usc.citius.servando.calendula.util.security.SecurityProvider;
import es.usc.citius.servando.calendula.util.HealthcareProviderTypeface;


public class BaseModule extends CalendulaModule {


    public static final String ID = "CALENDULA_BASE_MODULE";

    private static final String TAG = "BaseModule";

    /*----------*/


    @Override
    public String getId() {
        return ID;
    }

    public void initializeDatabase(Context ctx) {
        DB.init(ctx);
        DBRegistry.init(ctx);
    }

    @Override
    protected void onApplicationStartup(Context ctx) {
        PreferenceUtils.init(ctx);

        // initialize secured vault
        if (!Build.FINGERPRINT.equals("robolectric")) {
            SecurityProvider.init(ctx);
        }

        // initialize SQLite engine
        initializeDatabase(ctx);

        // create notification channels
        NotificationHelper.createNotificationChannels(ctx);

        // register medication intake receiver
        MedicationIntakeReceiver intakeMgr = new MedicationIntakeReceiver();
        Agenda.instance().registerReminderReceiver(EventType.MEDICATION_INTAKE, intakeMgr);
        Agenda.instance().registerListener(intakeMgr);
        Agenda.instance().start(ctx);

        //only required if you add a custom or generic font on your own
        Iconics.init(ctx);
        Iconics.registerFont(new GoogleMaterial());
        Iconics.registerFont(new CommunityMaterial());
        Iconics.registerFont(new PresentationsTypeface());
        Iconics.registerFont(new HealthcareProviderTypeface());

        //initialize job engine
        JobManager.create(ctx).addJobCreator(new CalendulaJobCreator());
        //schedule jobs
        CalendulaJob[] jobs = new CalendulaJob[]{
                new PurgeCacheJob()
        };
        CalendulaJobScheduler.scheduleJobs(jobs);
    }

}
