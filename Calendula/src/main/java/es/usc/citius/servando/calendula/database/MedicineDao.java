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
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.Where;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.drugdb.model.persistence.Prescription;
import es.usc.citius.servando.calendula.events.PersistenceEvents;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.PatientAlert;
import es.usc.citius.servando.calendula.persistence.PickupInfo;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.persistence.ScheduleUtils;
import es.usc.citius.servando.calendula.persistence.alerts.DrivingCautionAlert;
import es.usc.citius.servando.calendula.persistence.alerts.StockRunningOutAlert;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;
import es.usc.citius.servando.calendula.scheduling.Agenda;
import es.usc.citius.servando.calendula.util.alerts.DrivingAlertHandler;
import es.usc.citius.servando.calendula.util.alerts.StockAlertHandler;

public class MedicineDao extends GenericDao<Medicine, Long> {


    public MedicineDao(DatabaseHelper db) {
        super(db);
    }

    @Override
    public Dao<Medicine, Long> getConcreteDao() {
        try {
            return dbHelper.getMedicinesDao();
        } catch (SQLException e) {
            throw new RuntimeException("Error creating medicines dao", e);
        }
    }

    public List<Medicine> findAllForActivePatient(Context ctx) {
        return findAll(DB.patients().getActive(ctx));
    }

    public List<Medicine> findAll(Patient p) {
        // No active owner means no patient data; never query NULL-owned rows.
        return p == null || p.getId() == null
                ? Collections.<Medicine>emptyList() : findAll(p.getId());
    }


    public List<Medicine> findAll(Long patientId) {
        if (patientId == null) {
            return Collections.emptyList();
        }
        try {
            return dao.queryBuilder()
                    .where().eq(Medicine.COLUMN_PATIENT, patientId)
                    .query();
        } catch (SQLException e) {
            throw new RuntimeException("Error finding models", e);
        }
    }

    public List<Medicine> findAllByGroup(Long patientId, String[] groups) {
        // Avoid exposing unassigned records or building a malformed SQL IN ()
        // when a patient has no selected homogeneous medication groups.
        if (patientId == null || groups == null || groups.length == 0) {
            return Collections.emptyList();
        }
        try {
            return dao.queryBuilder()
                    .where().eq(Medicine.COLUMN_PATIENT, patientId).and().in(Medicine.COLUMN_HG, (Object[]) groups)
                    .query();
        } catch (SQLException e) {
            throw new RuntimeException("Error finding models", e);
        }
    }

    @Override
    public void fireEvent() {
        CalendulaApp.eventBus().post(PersistenceEvents.MEDICINE_EVENT);
    }


    @Override
    public void save(Medicine m) {
        Prescription p = m.isBoundToPrescription() ? DB.drugDB().prescriptions().findByCn(m.getCn()) : null;
        // on create, assign homogeneous group if possible
        if (m.getId() == null) {
            if (p != null && p.getHomogeneousGroup() != null) {
                m.setHomogeneousGroup(p.getHomogeneousGroup());
            }
        }
        // save the med
        super.save(m);
        // generate alerts if necessary
        StockAlertHandler.checkStockAlerts(m);
        DrivingAlertHandler.checkDrivingAlerts(m, p);
    }

    @Override
    public void saveAndFireEvent(Medicine model) {
        save(model);
        PersistenceEvents.ModelCreateOrUpdateEvent e = new PersistenceEvents.ModelCreateOrUpdateEvent(Medicine.class);
        e.model = model;
        CalendulaApp.eventBus().post(e);

    }

    /**
     * Remove a medicine and every live dependency as ONE SQLite unit. The
     * original cascade called removeSchedule() and removeAlert() from inside
     * its outer transaction; those functions cancel Android alarms, regenerate
     * schedules and prune unrelated routines before the outer SQL COMMIT.
     * A later failure could restore the rows but not those platform effects.
     */
    public void deleteCascade(final Medicine candidate, boolean fireEvent) {
        if (candidate == null || candidate.getId() == null
                || candidate.getId() <= 0L || candidate.getPatient() == null
                || candidate.getPatient().getId() == null
                || candidate.getPatient().getId() <= 0L) {
            throw new IllegalArgumentException("A persisted medicine and owner are required");
        }
        final Long medicineId = candidate.getId();
        final Long ownerId = candidate.getPatient().getId();

        Medicine deleted = (Medicine) DB.transaction(() -> {
            Medicine stored = DB.medicines().findById(medicineId);
            if (stored == null || DB.medicines().refresh(stored) != 1
                    || stored.getPatient() == null
                    || !ownerId.equals(stored.getPatient().getId())) {
                throw new SQLException("Medicine owner changed before deletion");
            }

            List<Schedule> schedules = DB.schedules().findByMedicine(stored);
            List<PickupInfo> pickups = DB.pickups().findByMedicine(stored);
            List<PatientAlert> alerts = DB.alerts().findBy(
                    PatientAlert.COLUMN_MEDICINE, medicineId);

            // Validate every dependency BEFORE deleting anything. A damaged
            // legacy row linked to another patient is never authorization to
            // destroy their medical data merely because IDs were reused.
            for (Schedule schedule : schedules) {
                if (schedule == null || schedule.getId() == null
                        || schedule.getPatient() == null
                        || !ownerId.equals(schedule.getPatient().getId())) {
                    throw new SQLException("Medicine schedule owner mismatch");
                }
            }
            for (PickupInfo pickup : pickups) {
                if (pickup == null || pickup.getId() == null
                        || pickup.getMedicine() == null
                        || !medicineId.equals(pickup.getMedicine().getId())) {
                    throw new SQLException("Medicine pickup identity mismatch");
                }
            }
            for (PatientAlert alert : alerts) {
                if (alert == null || alert.id() == null
                        || alert.getMedicine() == null
                        || !medicineId.equals(alert.getMedicine().getId())
                        || alert.getPatient() == null
                        || !ownerId.equals(alert.getPatient().getId())) {
                    throw new SQLException("Medicine alert patient mismatch");
                }
            }

            // Preserve decided doses, as removeSchedule() historically did;
            // delete pending occurrences and schedules without touching
            // AlarmManager, preferences or UI inside this transaction.
            for (Schedule selected : schedules) {
                Schedule current = DB.schedules().findById(selected.getId());
                if (current == null || DB.schedules().refresh(current) != 1
                        || current.getPatient() == null
                        || !ownerId.equals(current.getPatient().getId())) {
                    throw new SQLException("Schedule owner changed during medicine deletion");
                }
                DB.eventInstances().removeByRef(current.getId());
                if (DB.schedules().delete(current) != 1) {
                    throw new SQLException("Could not remove medicine schedule");
                }
            }
            for (PickupInfo pickup : pickups) {
                if (DB.pickups().deleteById(pickup.getId()) != 1) {
                    throw new SQLException("Could not remove medicine pickup");
                }
            }
            // Do NOT use AlertManager.removeAlert() for a medicine we are
            // erasing: it may unblock deleted schedules and rebuild doses.
            for (PatientAlert alert : alerts) {
                if (DB.alerts().deleteById(alert.id()) != 1) {
                    throw new SQLException("Could not remove medicine alert");
                }
            }
            if (DB.medicines().delete(stored) != 1) {
                throw new SQLException("Could not delete persisted medicine");
            }
            return stored;
        });

        // Only run nontransactional Android alarm cleanup and auto-routine
        // maintenance AFTER SQLite has committed the entire cascade.
        Agenda.instance().cleanReminders(CalendulaApp.getContext());
        ScheduleUtils.instance().removeUnlinkedAutoRoutines(
                deleted.getPatient(), false);
        if (fireEvent) {
            fireEvent();
        }
    }

    public Medicine findByGroupAndPatient(String group, Patient p) {
        try {
            QueryBuilder<Medicine, Long> qb = dao.queryBuilder();
            Where w = qb.where();
            w.and(w.eq(Medicine.COLUMN_HG, group), w.eq(Medicine.COLUMN_PATIENT, p));
            qb.setWhere(w);
            return qb.queryForFirst();
        } catch (SQLException e) {
            throw new RuntimeException("Error finding med", e);
        }
    }

    public Medicine findByCnAndPatient(String cn, Patient p) {
        try {
            QueryBuilder<Medicine, Long> qb = dao.queryBuilder();
            Where w = qb.where();
            w.and(w.eq(Medicine.COLUMN_CN, cn), w.eq(Medicine.COLUMN_PATIENT, p));
            qb.setWhere(w);
            return qb.queryForFirst();
        } catch (SQLException e) {
            throw new RuntimeException("Error finding med", e);
        }
    }

}
