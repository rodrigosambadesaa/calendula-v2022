package es.usc.citius.servando.calendula.login;

import android.content.Context;

import java.util.concurrent.Callable;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.modules.ModuleManager;
import es.usc.citius.servando.calendula.scheduling.Agenda;
import es.usc.citius.servando.calendula.healthcareprovider.modules.SetupDatabaseModule;
import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.PreferenceUtils;


public class LogoutHelper {

    private static final String TAG = "LogoutHelper";

    public static void clearData(final Context ctx) {
        LogUtil.d(TAG, "clearData() called with: ctx = [" + ctx + "]");
        // remove shared prefs
        PreferenceUtils.edit().clear().apply();
        LogUtil.d(TAG, "clearData: cleared prefs");
        // cancel all reminders
        Agenda.instance().deleteAllReminders(ctx);
        LogUtil.d(TAG, "clearData: cleared reminders");
        // drop tables
        DB.transaction(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                DB.dropAndCreateDatabase();
                return null;
            }
        });
        LogUtil.d(TAG, "clearData: dropped database, will re-setup med db now");
        ModuleManager.getInstance().run(new SetupDatabaseModule(), ctx);
    }

}
