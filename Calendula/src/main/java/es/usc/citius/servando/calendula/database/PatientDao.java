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
import androidx.annotation.Nullable;

import com.j256.ormlite.dao.Dao;

import java.sql.SQLException;

import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.events.PersistenceEvents;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Routine;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

public class PatientDao extends GenericDao<Patient, Long> {


    private static final String TAG = "PatientDao";

    public PatientDao(DatabaseHelper db) {
        super(db);
    }

    @Override
    public Dao<Patient, Long> getConcreteDao() {
        try {
            return dbHelper.getPatientDao();
        } catch (SQLException e) {
            throw new RuntimeException("Error creating patients dao", e);
        }
    }

    @Override
    public void saveAndFireEvent(Patient p) {

        Object event = p.getId() == null ? new PersistenceEvents.UserCreateEvent(p) : new PersistenceEvents.UserUpdateEvent(p);
        save(p);
        CalendulaApp.eventBus().post(event);

    }

    // Manage active patient through preferences

    public boolean isActive(Patient p, Context ctx) {

        Long activeId = PreferenceUtils.getLong(PreferenceKeys.PATIENTS_ACTIVE, -1);
        return activeId.equals(p.getId());
    }

    public Patient getActive(Context ctx) {
        long id = PreferenceUtils.getLong(PreferenceKeys.PATIENTS_ACTIVE, -1);
        Patient p;
        if (id != -1) {
            p = findById(id);
            if (p == null) {
                p = getDefault();
                setActive(p);
            }
            return p;
        } else {
            return getDefault();
        }
    }

    @Nullable
    public Patient getDefault() {
        return findOneBy(Patient.COLUMN_DEFAULT, true);
    }

    /**
     * Marks a {@link Patient} as being active in the application.
     *
     * @param patient the patient
     */
    public void setActive(Patient patient) {
        PreferenceUtils.edit()
                .putLong(PreferenceKeys.PATIENTS_ACTIVE.key(), patient.getId())
                .apply();
        CalendulaApp.eventBus().post(new PersistenceEvents.ActiveUserChangeEvent(patient));
    }

    /**
     * @see #setActive(Patient)
     */
    public void setActiveById(Long id) {
        Patient patient = findById(id);
        PreferenceUtils.edit()
                .putLong(PreferenceKeys.PATIENTS_ACTIVE.key(), patient.getId())
                .apply();
        CalendulaApp.eventBus().post(new PersistenceEvents.ActiveUserChangeEvent(patient));
    }


    public void removeCascade(Patient p) {
        // remove all data
        removeAllStuff(p);
        // remove patient
        DB.patients().remove(p);

    }

    public void removeAllStuff(Patient p) {
        // Never run a patient-data deletion using an absent or unsaved owner:
        // filtering by NULL would also match unrelated, unassigned records.
        final Long patientId = p == null ? null : p.getId();
        if (patientId == null) {
            throw new IllegalArgumentException("A persisted patient is required for deletion");
        }

        // Query the owner's rows in SQLite. Comparing boxed Long IDs with ==
        // previously checked object identity, potentially leaving another
        // Long instance with the same value undeleted (notably IDs > 127).
        // Do not scan or mutate another patient's medications or routines.
        for (Medicine m : DB.medicines().findAll(patientId)) {
            // This also removes the medication's schedules, pickups and alerts.
            DB.medicines().deleteCascade(m, true);
        }
        for (Routine r : DB.routines().findAll(patientId)) {
            DB.routines().remove(r);
        }
    }
}
