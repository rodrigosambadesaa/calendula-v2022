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

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.Where;

import java.sql.SQLException;
import java.util.List;

import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.PatientAlert;
import es.usc.citius.servando.calendula.util.LogUtil;

public class PatientAlertDao extends GenericDao<PatientAlert, Long> {

    private static final String TAG = "AlertDao";

    private Dao<PatientAlert, Long> daoInstance = null;

    public PatientAlertDao(DatabaseHelper db) {
        super(db);
    }

    @Override
    public Dao<PatientAlert, Long> getConcreteDao() {
        try {
            if (daoInstance == null)
                daoInstance = dbHelper.getDao(PatientAlert.class);
            return daoInstance;
        } catch (SQLException e) {
            throw new RuntimeException("Error creating patients dao", e);
        }
    }

    /**
     * An alert's medicine ID alone is not a patient authorization boundary.
     * Verify its current persisted owner before reading any clinical alert.
     */
    private Medicine requireMedicineOwner(Medicine requested) {
        if (requested == null || requested.getId() == null
                || requested.getId() <= 0L || requested.getPatient() == null
                || requested.getPatient().getId() == null
                || requested.getPatient().getId() <= 0L) {
            throw new IllegalArgumentException("An owned persisted medicine is required");
        }
        final Long ownerId = requested.getPatient().getId();
        Medicine persisted = DB.medicines().findById(requested.getId());
        if (persisted == null || DB.medicines().refresh(persisted) != 1
                || persisted.getPatient() == null
                || !ownerId.equals(persisted.getPatient().getId())) {
            throw new IllegalStateException("Medicine owner is no longer current");
        }
        return persisted;
    }

    /** Reject corrupt cross-owner rows instead of returning/discarding them. */
    static boolean belongsToMedicineOwner(PatientAlert alert, Long medicineId, Long patientId) {
        return alert != null && medicineId != null && medicineId > 0L
                && patientId != null && patientId > 0L
                && alert.getMedicine() != null
                && medicineId.equals(alert.getMedicine().getId())
                && alert.getPatient() != null
                && patientId.equals(alert.getPatient().getId());
    }

    private List<PatientAlert> requireSameOwner(
            Medicine medicine, List<PatientAlert> results) {
        Long medicineId = medicine.getId();
        Long patientId = medicine.getPatient().getId();
        for (PatientAlert result : results) {
            if (!belongsToMedicineOwner(result, medicineId, patientId)) {
                throw new IllegalStateException("Alert row belongs to another patient");
            }
        }
        return results;
    }

    public List<PatientAlert> findByMedicineAndLevel(Medicine m, int level) {
        Medicine stored = requireMedicineOwner(m);
        try {
            final PreparedQuery<PatientAlert> q = dao.queryBuilder().where()
                    .eq(PatientAlert.COLUMN_MEDICINE, stored)
                    .and().eq(PatientAlert.COLUMN_LEVEL, level)
                    .prepare();
            return requireSameOwner(stored, dao.query(q));
        } catch (SQLException e) {
            LogUtil.e(TAG, "findByMedicineAndLevel: ", e);
            throw new RuntimeException("Cannot retrieve alerts", e);
        }
    }

    public List<PatientAlert> findByMedicineSortByLevel(Medicine m) {
        Medicine stored = requireMedicineOwner(m);
        try {
            final QueryBuilder<PatientAlert, Long> qb = dao.queryBuilder();
            qb.where().eq(PatientAlert.COLUMN_MEDICINE, stored);
            qb.orderBy(PatientAlert.COLUMN_LEVEL, false);
            return requireSameOwner(stored, dao.query(qb.prepare()));
        } catch (SQLException e) {
            LogUtil.e(TAG, "findByMedicineSortByLevel: ", e);
            throw new RuntimeException("Cannot retrieve alerts", e);
        }
    }

    public List<PatientAlert> findByPatientMedicineAndType(
            Patient p, Medicine m, String className) {
        Medicine stored = requireMedicineOwner(m);
        if (p == null || p.getId() == null
                || !p.getId().equals(stored.getPatient().getId())
                || className == null || className.isEmpty()) {
            throw new IllegalArgumentException("Patient and alert type must match the medicine");
        }
        try {
            final PreparedQuery<PatientAlert> query = dao.queryBuilder().where()
                    .eq(PatientAlert.COLUMN_TYPE, className).and()
                    .eq(PatientAlert.COLUMN_PATIENT, stored.getPatient()).and()
                    .eq(PatientAlert.COLUMN_MEDICINE, stored).prepare();
            // An SQLite failure must never masquerade as 'no clinical alert'.
            return requireSameOwner(stored, dao.query(query));
        } catch (SQLException e) {
            LogUtil.e(TAG, "findByPatientMedicineAndType: ", e);
            throw new RuntimeException("Cannot retrieve patient alerts", e);
        }
    }

    public List<PatientAlert> findByMedicineAndType(Medicine m, String type) {
        Medicine stored = requireMedicineOwner(m);
        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("Alert type is required");
        }
        try {
            final PreparedQuery<PatientAlert> q = dao.queryBuilder().where()
                    .eq(PatientAlert.COLUMN_MEDICINE, stored)
                    .and().eq(PatientAlert.COLUMN_TYPE, type)
                    .prepare();
            return requireSameOwner(stored, dao.query(q));
        } catch (SQLException e) {
            LogUtil.e(TAG, "findByMedicineAndType: ", e);
            throw new RuntimeException("Cannot retrieve alerts", e);
        }
    }

}
