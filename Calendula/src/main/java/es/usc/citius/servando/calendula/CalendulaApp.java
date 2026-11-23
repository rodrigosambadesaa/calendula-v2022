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

package es.usc.citius.servando.calendula;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.app.Application;

import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.security.ProviderInstaller;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.lang.ref.WeakReference;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

import javax.net.ssl.SSLContext;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.modules.ModuleManager;
import es.usc.citius.servando.calendula.util.LocaleHelper;
import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.NetworkUtils;
import es.usc.citius.servando.calendula.util.debug.StethoHelper;

// Android API 23+ loads secondary DEX files natively; legacy support loader is obsolete.
public class CalendulaApp extends Application {

    // INTENTS
    public static final String INTENT_EXTRA_ACTION = "action";
    public static final String INTENT_EXTRA_ROUTINE_ID = "routine_id";
    public static final String INTENT_EXTRA_MEDICINE_ID = "medicine_id";
    public static final String INTENT_EXTRA_SCHEDULE_ID = "schedule_id";
    public static final String INTENT_EXTRA_SCHEDULE_TIME = "schedule_time";
    public static final String INTENT_EXTRA_DELAY_ROUTINE_ID = "delay_routine_id";
    public static final String INTENT_EXTRA_DELAY_SCHEDULE_ID = "delay_schedule_id";
    public static final String INTENT_EXTRA_DATE = "date";
    public static final String INTENT_EXTRA_POSITION = "position";
    public static final String INTENT_EXTRA_PATIENT_ID = "paciente";
    // ACTIONS
    public static final int ACTION_ROUTINE_TIME = 1;
    public static final int ACTION_DAILY_ALARM = 2;
    public static final int ACTION_ROUTINE_DELAYED_TIME = 3;
    public static final int ACTION_DELAY_ROUTINE = 4;
    public static final int ACTION_CANCEL_ROUTINE = 5;
    public static final int ACTION_HOURLY_SCHEDULE_TIME = 6;
    public static final int ACTION_HOURLY_SCHEDULE_DELAYED_TIME = 7;
    public static final int ACTION_DELAY_HOURLY_SCHEDULE = 8;
    public static final int ACTION_CANCEL_HOURLY_SCHEDULE = 9;
    public static final int ACTION_CHECK_PICKUPS_ALARM = 10;
    public static final int ACTION_CONFIRM_ALL_ROUTINE = 11;
    public static final int ACTION_CONFIRM_ALL_SCHEDULE = 12;
    // REQUEST CODES
    public static final int RQ_SHOW_ROUTINE = 1;
    public static final int RQ_DELAY_ROUTINE = 2;
    private static final String TAG = "CalendulaApp";
    public static boolean disableReceivers = false;

    private static WeakReference<EventBus> eventBusRef;
    // Assigned only from getApplicationContext() in onCreate(); process-lifetime storage is intentional.
    @SuppressLint("StaticFieldLeak")
    private static Context mContext;

    public static EventBus eventBus() {
        if (eventBusRef == null || eventBusRef.get() == null) {
            eventBusRef = new WeakReference<>(EventBus.getDefault());
        }
        return eventBusRef.get();
    }

    public static Context getContext() {
        return mContext;
    }

    /**
     * Legacy raw-file export is deliberately disabled.
     *
     * Copying SQLite's main database file while the application is running is
     * not a consistent backup: committed data can still live in the WAL, and
     * concurrent writes can invalidate the copied image. Never expose a
     * potentially incomplete medication/patient database as a valid export.
     *
     * A future replacement must use a supported consistent SQLite snapshot,
     * validate the result and publish it only after an atomic file commit.
     *
     * @deprecated Unsafe export API retained only to fail explicitly for
     *             callers compiled against the historical method.
     */
    @Deprecated
    public void exportDatabase(Context context, String databaseName, File out) {
        throw new UnsupportedOperationException(
                "Raw SQLite database export is unsafe and is disabled");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (!Build.FINGERPRINT.equals("robolectric") && BuildConfig.DEBUG) {
            new StethoHelper().init(this);
        }

        final Context applicationContext = getApplicationContext();
        mContext = applicationContext;
        NetworkUtils.startNetworkObserver(applicationContext);

        LogUtil.d(TAG, "Application started");

        try {
            LogUtil.d(TAG, "Application flavor is \"" + BuildConfig.FLAVOR + "\"");
            // Build flavors are program identifiers, not natural-language text. Locale-sensitive
            // casing can turn "ci" into "Cİ" on Turkish devices and break enum lookup.
            final String flavor = BuildConfig.FLAVOR.toUpperCase(Locale.ROOT);
            ModuleManager.getInstance().runModules(flavor, applicationContext);
        } catch (IllegalArgumentException | IllegalStateException e) {
            LogUtil.e(TAG, "Unable to load module configuration");
            LogUtil.w(TAG, "onCreate: Loading default module configuration instead");
            ModuleManager.getInstance().runDefaultModules(applicationContext);
        }
    }

    @Override
    public void onTerminate() {
        NetworkUtils.stopNetworkObserver();
        DB.dispose();
        super.onTerminate();
    }
}
