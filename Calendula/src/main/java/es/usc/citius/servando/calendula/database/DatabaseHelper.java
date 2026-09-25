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


import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

import java.sql.SQLException;

import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.drugdb.model.persistence.ATCCode;
import es.usc.citius.servando.calendula.drugdb.model.persistence.ActiveIngredient;
import es.usc.citius.servando.calendula.drugdb.model.persistence.ContentUnit;
import es.usc.citius.servando.calendula.drugdb.model.persistence.Excipient;
import es.usc.citius.servando.calendula.drugdb.model.persistence.HomogeneousGroup;
import es.usc.citius.servando.calendula.drugdb.model.persistence.PackageType;
import es.usc.citius.servando.calendula.drugdb.model.persistence.Prescription;
import es.usc.citius.servando.calendula.drugdb.model.persistence.PrescriptionActiveIngredient;
import es.usc.citius.servando.calendula.drugdb.model.persistence.PrescriptionExcipient;
import es.usc.citius.servando.calendula.drugdb.model.persistence.PresentationForm;
import es.usc.citius.servando.calendula.persistence.AllergyGroup;
import es.usc.citius.servando.calendula.persistence.HtmlCacheEntry;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.PatientAlert;
import es.usc.citius.servando.calendula.persistence.PatientAllergen;
import es.usc.citius.servando.calendula.persistence.PickupInfo;
import es.usc.citius.servando.calendula.persistence.Routine;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedEntity;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DispensationInfoEntity;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DosageEntity;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DosageEntryEntity;
import es.usc.citius.servando.calendula.util.LogUtil;

/**
 * Database helper class used to manage the creation and upgrading of your database. This class also  provides
 * the DAOs used by the other classes.
 */
public class DatabaseHelper extends OrmLiteSqliteOpenHelper {

    // any time you make changes to your database objects, you may have to increase the database version
    public static final int DATABASE_VERSION = 3;
    private static final String TAG = "DatabaseHelper";
    // name of the database file for our application
    private static final String DATABASE_NAME = DB.DB_NAME;
    // List of persisted classes to simplify table creation
    public Class<?>[] persistedClasses = new Class<?>[]{
            Routine.class,
            Medicine.class,

            PickupInfo.class,
            Patient.class,
            HtmlCacheEntry.class,
            ActiveIngredient.class,
            ATCCode.class,
            ContentUnit.class,
            Excipient.class,
            HomogeneousGroup.class,
            PackageType.class,
            Prescription.class,
            PresentationForm.class,
            PrescriptionActiveIngredient.class,
            PrescriptionExcipient.class,
            PatientAllergen.class,
            PatientAlert.class,
            AllergyGroup.class,
            // Healthcare provider
            ActiveMedEntity.class,
            DispensationInfoEntity.class,
            DosageEntity.class,
            DosageEntryEntity.class,
            Schedule.class,
            EventInstance.class,
            EventReminder.class
    };

    private Dao<Medicine, Long> medicinesDao = null;
    private Dao<Routine, Long> routinesDao = null;

    private Dao<Prescription, Long> prescriptionsDao = null;
    private Dao<HomogeneousGroup, Long> homogeneousGroupsDao = null;
    private Dao<PickupInfo, Long> pickupInfoDao = null;
    private Dao<Patient, Long> patientDao = null;
    private Dao<Schedule, Long> schedulesDao = null;
    private Dao<EventInstance, Long> eventInstanceDao = null;
    private Dao<EventReminder, Long> eventReminder = null;

    private final Context ctx;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        ctx = context;
    }

    /**
     * This is called when the database is first created. Usually you should call createTable statements here to create
     * the tables that will store your data.
     */
    @Override
    public void onCreate(SQLiteDatabase db, ConnectionSource connectionSource) {
        try {
            LogUtil.i(TAG, "onCreate");

            for (Class<?> c : persistedClasses) {
                LogUtil.d(TAG, "Creating table for " + c.getSimpleName());
                TableUtils.createTable(connectionSource, c);
            }

        } catch (SQLException e) {
            LogUtil.e(TAG, "Can't create database", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * This is called when your application is upgraded and it has a higher version number. This allows you to adjust
     * the various data to match the new version number.
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, ConnectionSource connectionSource, int oldVersion, int newVersion) {
        try {
            LogUtil.i(TAG, "onUpgrade");
            LogUtil.d(TAG, "OldVersion: " + oldVersion + ", newVersion: " + newVersion);
            Dao<ActiveMedEntity, Long> dao = getDao(ActiveMedEntity.class);
            if (oldVersion < 2) {
                // we added the VisualizationType column in version 2
                dao.executeRaw("ALTER TABLE `" + ActiveMedEntity.TABLENAME + "` ADD COLUMN " + ActiveMedEntity.COLUMN_VISUALIZATION_TYPE + " INTEGER;");
            }
            if (oldVersion < 3) {
                // Version 3 stores the original URL alongside its legacy 32-bit
                // hash so cache lookups can reject String.hashCode() collisions.
                Dao<HtmlCacheEntry, Long> htmlCacheDao = getDao(HtmlCacheEntry.class);
                htmlCacheDao.executeRaw(
                        "ALTER TABLE `HtmlCache` ADD COLUMN `"
                                + HtmlCacheEntry.COLUMN_URL
                                + "` VARCHAR;");
            }
        } catch (Exception e) {
            LogUtil.e(TAG, "Can't upgrade databases", e);
            try {
                LogUtil.d(TAG, "Will try to recreate db...");
                dropAndCreateAllTables();
            } catch (Exception ex) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Returns the Database Access Object (DAO) for our Medicines class. It will create it or just give the cached
     * value.
     */
    public Dao<Medicine, Long> getMedicinesDao() throws SQLException {
        if (medicinesDao == null) {
            medicinesDao = getDao(Medicine.class);
        }
        return medicinesDao;
    }

    /**
     * Returns the Database Access Object (DAO) for our Routines class. It will create it or just give the cached
     * value.
     */
    public Dao<Routine, Long> getRoutinesDao() throws SQLException {
        if (routinesDao == null) {
            routinesDao = getDao(Routine.class);
        }
        return routinesDao;
    }


    /**
     * Returns the Database Access Object (DAO) for our DailyScheduleItem class. It will create it or just give the cached
     * value.
     */
    public Dao<Prescription, Long> getPrescriptionsDao() throws SQLException {
        if (prescriptionsDao == null) {
            prescriptionsDao = getDao(Prescription.class);
        }
        return prescriptionsDao;
    }

    /**
     * Returns the Database Access Object (DAO) for our HomogeneousGroup class. It will create it or just give the cached
     * value.
     */
    public Dao<HomogeneousGroup, Long> getHomogeneousGroupsDao() throws SQLException {
        if (homogeneousGroupsDao == null) {
            homogeneousGroupsDao = getDao(HomogeneousGroup.class);
        }
        return homogeneousGroupsDao;
    }

    /**
     * Returns the Database Access Object (DAO) for our PickupInfo class. It will create it or just give the cached
     * value.
     */
    public Dao<PickupInfo, Long> getPickupInfosDao() throws SQLException {
        if (pickupInfoDao == null) {
            pickupInfoDao = getDao(PickupInfo.class);
        }
        return pickupInfoDao;
    }

    /**
     * Returns the Database Access Object (DAO) for our User class. It will create it or just give the cached
     * value.
     */
    public Dao<Patient, Long> getPatientDao() throws SQLException {
        if (patientDao == null) {
            patientDao = getDao(Patient.class);
        }
        return patientDao;
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }

    public void dropAndCreateAllTables() {

        LogUtil.i(TAG, "Dropping all tables...");
        for (Class<?> c : persistedClasses) {
            LogUtil.d(TAG, "Dropping table " + c.getSimpleName());
            try {
                TableUtils.dropTable(connectionSource, c, true);
            } catch (SQLException e) {
                // ignore
                LogUtil.e(TAG, "Erro dropping table " + c.getSimpleName());
            }

        }

        try {

            LogUtil.i(TAG, "Creating tables...");
            for (Class<?> c : persistedClasses) {
                LogUtil.d(TAG, "Creating table " + c.getSimpleName());
                TableUtils.createTable(connectionSource, c);
            }

        } catch (SQLException e) {
            LogUtil.e(TAG, "Can't recreate database", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Close the database connections and clear any cached DAOs.
     */
    @Override
    public void close() {
        super.close();
        medicinesDao = null;
        routinesDao = null;

        prescriptionsDao = null;
        homogeneousGroupsDao = null;
        pickupInfoDao = null;
        patientDao = null;
    }

    /**
     * Creates a default patient.
     *
     * @return the created Patient
     * @throws SQLException if anything goes wrong
     */
    public Patient createDefaultPatient() throws SQLException {
        // Create a default patient
        Patient p = new Patient();
        p.setName(ctx.getString(R.string.default_user_name));
        p.setDefault(true);
        getPatientDao().create(p);
        return p;
    }

    public Dao<Schedule, Long> getSchedulesDao() throws SQLException {
        if (schedulesDao == null) {
            schedulesDao = getDao(Schedule.class);
        }
        return schedulesDao;
    }

    public Dao<EventInstance, Long> getEventInstanceDao() throws SQLException {
        if (eventInstanceDao == null) {
            eventInstanceDao = getDao(EventInstance.class);
        }
        return eventInstanceDao;
    }

    public Dao<EventReminder, Long> getEventReminderDao() throws SQLException {
        if (eventReminder == null) {
            eventReminder = getDao(EventReminder.class);
        }
        return eventReminder;
    }
}
