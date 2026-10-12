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

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.Where;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.events.PersistenceEvents;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.alerts.StockAlertHandler;

public class ScheduleDao extends GenericDao<Schedule, Long> {

    private static final String TAG = "ScheduleDao";

    public ScheduleDao(DatabaseHelper db) {
        super(db);
    }

    public List<Schedule> findAllForActivePatient(Context ctx) {
        return findAll(DB.patients().getActive(ctx));
    }

    public List<Schedule> findAll(Patient p) {
        // No active owner means no patient data; never query NULL-owned rows.
        return p == null || p.getId() == null
                ? Collections.<Schedule>emptyList() : findAll(p.getId());
    }


    public List<Schedule> findAll(Long patientId) {
        if (patientId == null) {
            return Collections.emptyList();
        }
        try {
            return dao.queryBuilder()
                    .where().eq(Schedule.COLUMN_PATIENT, patientId)
                    .query();
        } catch (SQLException e) {
            throw new RuntimeException("Error finding models", e);
        }
    }


    @Override
    public Dao<Schedule, Long> getConcreteDao() {
        try {
            return dbHelper.getSchedulesDao();
        } catch (SQLException e) {
            throw new RuntimeException("Error creating medicines dao", e);
        }
    }

    public List<Schedule> findByMedicine(Medicine m) {
        return findBy(Schedule.COLUMN_MEDICINE, m.getId());
    }

    @Override
    public void fireEvent() {
        CalendulaApp.eventBus().post(PersistenceEvents.SCHEDULE_EVENT);
    }


    public Schedule findByMedicineAndPatient(Medicine m, Patient p) {
        try {
            QueryBuilder<Schedule, Long> qb = dao.queryBuilder();
            Where w = qb.where();
            w.and(w.eq(Schedule.COLUMN_MEDICINE, m), w.eq(Schedule.COLUMN_PATIENT, p));
            qb.setWhere(w);
            return qb.queryForFirst();
        } catch (SQLException e) {
            throw new RuntimeException("Error finding schedule", e);
        }
    }

    /**
     * Alerts must never read blocked schedules using a stale medicine owner.
     * Resolve the current persisted medicine before a patient-scoped query;
     * a reassigned/deleted medicine must not disclose either patient's plan.
     */
    public List<Schedule> findByMedicineAndState(Medicine medicine, Schedule.ScheduleState state) {
        if (medicine == null || medicine.getId() == null || medicine.getId() <= 0L
                || medicine.getPatient() == null
                || medicine.getPatient().getId() == null || state == null) {
            return Collections.emptyList();
        }
        // Snapshot the expected owner BEFORE refresh: ORMLite can return the
        // very same cached instance, and refresh may mutate its patient field.
        final Long expectedOwnerId = medicine.getPatient().getId();
        Medicine stored = DB.medicines().findById(medicine.getId());
        if (stored == null || DB.medicines().refresh(stored) != 1
                || stored.getPatient() == null
                || !expectedOwnerId.equals(stored.getPatient().getId())) {
            return Collections.emptyList();
        }
        try {
            PreparedQuery<Schedule> preparedQuery = dao.queryBuilder().where()
                    .eq(Schedule.COLUMN_MEDICINE, stored)
                    .and().eq(Schedule.COLUMN_PATIENT, stored.getPatient())
                    .and().raw(Schedule.COLUMN_STATE + " LIKE '%" + state.value() + "%'")
                    .prepare();
            return dao.query(preparedQuery);
        } catch (SQLException e) {
            LogUtil.e(TAG, "findByMedicineAndState failed");
            throw new RuntimeException("Error finding schedules", e);
        }
    }

    @Override
    public void save(Schedule model) {
        super.save(model);
        if (model.getMedicine() != null) {
            StockAlertHandler.checkStockAlerts(model.getMedicine());
        }
    }
}
