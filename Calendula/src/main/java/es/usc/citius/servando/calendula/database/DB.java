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

package es.usc.citius.servando.calendula.database;

import android.annotation.SuppressLint;
import android.content.Context;

import com.j256.ormlite.misc.TransactionManager;

import java.util.concurrent.Callable;

import es.usc.citius.servando.calendula.drugdb.model.database.DrugDBModule;
import es.usc.citius.servando.calendula.healthcareprovider.database.HealthcareProviderDBModule;
import es.usc.citius.servando.calendula.util.LogUtil;


public class DB {


    private static final String TAG = "DB";

    // Database name
    public static String DB_NAME = "calendula.db";
    // initialized flag
    public static boolean initialized = false;

    // DatabaseManeger reference
    private static DatabaseManager<DatabaseHelper> manager;
    // SQLite DB Helper. init() canonicalizes callers to the application context.
    @SuppressLint("StaticFieldLeak")
    private static DatabaseHelper db;

    // Medicines DAO
    private static MedicineDao Medicines;
    // Routines DAO
    private static RoutineDao Routines;
    // Schedules DAO
    private static ScheduleDao Schedules;
    // Pickups DAO
    private static PickupInfoDao Pickups;
    // Patients DAO
    private static PatientDao Patients;
    // Drug DB module
    private static DrugDBModule DrugDB;
    // Alerts DAO
    private static PatientAlertDao PatientAlerts;
    // Allergens DAO
    private static PatientAllergenDao PatientAllergens;
    // Allergy group DAO
    private static AllergyGroupDao AllergyGroups;
    // Event Instances
    private static EventInstanceDao EventInstances;
    // Event Reminders
    private static EventReminderDao EventReminders;

    // Healthcare Provider DB Module;
    private static HealthcareProviderDBModule healthcareProviderDB;

    /**
     * Initialize database and DAOs
     */
    public synchronized static void init(Context context) {

        if (!initialized) {
            Context applicationContext = context.getApplicationContext();
            Context safeContext = applicationContext != null ? applicationContext : context;
            manager = new DatabaseManager<>();
            try {
                db = manager.getHelper(safeContext, DatabaseHelper.class);

                // May throw on a failed migration or inaccessible storage.
                // Never advertise the singleton as ready before this succeeds.
                db.getReadableDatabase().enableWriteAheadLogging();

                Medicines = new MedicineDao(db);
            Routines = new RoutineDao(db);

            Pickups = new PickupInfoDao(db);
            Patients = new PatientDao(db);
            DrugDB = DrugDBModule.getInstance();
            PatientAlerts = new PatientAlertDao(db);
            PatientAllergens = new PatientAllergenDao(db);
            AllergyGroups = new AllergyGroupDao(db);
            Schedules = new ScheduleDao(db);
            EventInstances = new EventInstanceDao(db);
            EventReminders = new EventReminderDao(db);
                healthcareProviderDB = HealthcareProviderDBModule.getInstance();
                // The flag must be last: failed database opens remain retryable.
                initialized = true;
                LogUtil.v(TAG, "DB initialized " + DB.DB_NAME);
            } catch (RuntimeException | Error failure) {
                // Do not leave a partially initialized or cached helper that
                // could mask the original upgrade/storage error on next start.
                if (db != null) {
                    try {
                        manager.releaseHelper(db);
                    } catch (RuntimeException cleanupFailure) {
                        failure.addSuppressed(cleanupFailure);
                    }
                }
                db = null;
                manager = null;
                Medicines = null;
                Routines = null;
                Schedules = null;
                Pickups = null;
                Patients = null;
                DrugDB = null;
                PatientAlerts = null;
                PatientAllergens = null;
                AllergyGroups = null;
                EventInstances = null;
                EventReminders = null;
                healthcareProviderDB = null;
                initialized = false;
                throw failure;
            }
        }
    }

    /**
     * Dispose DB and DAOs
     */
    public synchronized static void dispose() {
        initialized = false;
        db.close();
        manager.releaseHelper(db);
        LogUtil.v(TAG, "DB disposed");
    }

    public static DatabaseHelper helper() {
        return db;
    }

    public static Object transaction(Callable<?> callable) {
        try {
            return TransactionManager.callInTransaction(db.getConnectionSource(), callable);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public static MedicineDao medicines() {
        return Medicines;
    }

    public static RoutineDao routines() {
        return Routines;
    }

    public static PickupInfoDao pickups() {
        return Pickups;
    }

    public static PatientDao patients() {
        return Patients;
    }

    public static DrugDBModule drugDB() {
        return DrugDB;
    }

    public static PatientAlertDao alerts() {
        return PatientAlerts;
    }

    public static PatientAllergenDao patientAllergens() {
        return PatientAllergens;
    }

    public static AllergyGroupDao allergyGroups() {
        return AllergyGroups;
    }

    public static EventInstanceDao eventInstances() {
        return EventInstances;
    }

    public static EventReminderDao eventReminders() {
        return EventReminders;
    }

    public static ScheduleDao schedules() {
        return Schedules;
    }

    public static HealthcareProviderDBModule healthcareProviderDB() {
        return healthcareProviderDB;
    }

    public static void dropAndCreateDatabase() {
        db.dropAndCreateAllTables();
    }
}
