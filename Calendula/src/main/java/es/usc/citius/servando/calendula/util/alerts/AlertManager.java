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

package es.usc.citius.servando.calendula.util.alerts;

import java.util.List;
import java.sql.SQLException;

import org.joda.time.DateTime;

import es.usc.citius.servando.calendula.scheduling.Agenda;
import java.util.concurrent.Callable;

import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.events.PersistenceEvents;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.PatientAlert;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.persistence.ScheduleUtils;
import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.PatientOwnerGuard;

import static es.usc.citius.servando.calendula.persistence.PatientAlert.Level;


public class AlertManager {

    private static final String TAG = "AlertManager";

    public static void createAlert(final PatientAlert alert) {
        if (alert == null) {
            throw new IllegalArgumentException("A persisted clinical alert is required");
        }
        final boolean high = alert.getLevel() == Level.HIGH;
        // Alert creation and schedule/occurrence blocking commit together.
        // ORMLite leaves a generated ID on the Java object after SQL ROLLBACK.
        // Restore the original identity so retrying the same clinical alert
        // draft cannot accidentally UPDATE a row that was never committed.
        final Long originalId = alert.id();
        try {
            DB.transaction(() -> {
                // Every clinical alert carries a patient/medicine link.
                // LOW/MEDIUM driving and stock alerts must not bypass the
                // same durable owner verification already used for HIGH.
                // Numeric SQLite IDs can be reused after a database restore;
                // validate both IDs and stable patient codes INSIDE SQLite
                // before a single alert row is written.
                Patient expected = alert.getPatient();
                Medicine requestedMedicine = alert.getMedicine();
                if (expected == null || expected.getId() == null
                        || expected.getId() <= 0L
                        || requestedMedicine == null || requestedMedicine.getId() == null
                        || requestedMedicine.getId() <= 0L
                        || !sameStablePatient(expected,
                                DB.patients().findById(expected.getId()))
                        || !sameStablePatient(expected, requestedMedicine.getPatient())) {
                    throw new SQLException("Clinical alert is missing a persisted stable owner or medicine");
                }
                Medicine verifiedMedicine = DB.medicines().findById(requestedMedicine.getId());
                if (verifiedMedicine == null
                        || DB.medicines().refresh(verifiedMedicine) != 1
                        || !sameStablePatient(expected, verifiedMedicine.getPatient())) {
                    throw new SQLException("Clinical alert patient differs from persisted medicine owner");
                }
                DB.alerts().save(alert);
                if (high) {
                    blockSchedulesForMedicineInSql(verifiedMedicine);
                }
                return null;
            });
        } catch (RuntimeException failure) {
            if (originalId == null) {
                alert.setId(null);
            }
            throw failure;
        }
        // AlarmManager and UI observers must only run after SQL COMMIT.
        if (high) {
            Agenda.instance().cleanReminders(CalendulaApp.getContext());
            CalendulaApp.eventBus().post(PersistenceEvents.SCHEDULE_EVENT);
        }
        CalendulaApp.eventBus().post(PersistenceEvents.ALERT_EVENT);
    }

    public static void removeAlert(final PatientAlert alert) {
        if (alert == null || alert.id() == null || alert.id() <= 0L) {
            throw new IllegalArgumentException("A persisted alert ID is required");
        }

        final AlertRemovalResult result = (AlertRemovalResult) DB.transaction(() -> {
            PatientAlert current = DB.alerts().findById(alert.id());
            if (current == null) {
                return null; // Already removed; never unblock anything.
            }
            Medicine linked = current.getMedicine() == null
                    || current.getMedicine().getId() == null ? null
                    : DB.medicines().findById(current.getMedicine().getId());
            Patient persistedOwner = current.getPatient() == null
                    || current.getPatient().getId() == null ? null
                    : DB.patients().findById(current.getPatient().getId());
            if (!sameAlertIdentity(alert, current)
                    || !PatientOwnerGuard.sameStableIdentity(
                            alert.getPatient(), persistedOwner)
                    || (current.getLevel() == Level.HIGH
                            && (linked == null
                                || !PatientOwnerGuard.sameStableIdentity(
                                        alert.getPatient(), linked.getPatient())))) {
                throw new IllegalStateException("Alert identity changed before deletion");
            }

            if (DB.alerts().deleteById(current.id()) != 1) {
                throw new SQLException("Alert deletion did not remove exactly one row");
            }

            List<Schedule> toRebuild = new java.util.ArrayList<>();
            if (current.getLevel() == Level.HIGH) {
                // Observe the remaining HIGH warnings only *after* deleting
                // this one, on the same SQLite connection. A second HIGH
                // alert must keep the schedule blocked.
                List<PatientAlert> remaining = DB.alerts().findByMedicineAndLevel(
                        linked, Level.HIGH);
                if (remaining.isEmpty()) {
                    List<Schedule> blocked = DB.schedules().findByMedicineAndState(
                            linked, Schedule.ScheduleState.BLOCKED);
                    for (Schedule selected : blocked) {
                        if (selected == null || selected.getId() == null
                                || !PatientOwnerGuard.sameStableIdentity(
                                        alert.getPatient(), selected.getPatient())) {
                            throw new SQLException("Blocked schedule owner mismatch");
                        }
                        Schedule stored = DB.schedules().findById(selected.getId());
                        if (stored == null || DB.schedules().refresh(stored) != 1
                                || !PatientOwnerGuard.sameStableIdentity(
                                        alert.getPatient(), stored.getPatient())
                                || stored.getMedicine() == null
                                || !linked.getId().equals(stored.getMedicine().getId())
                                || !stored.hasState(Schedule.ScheduleState.BLOCKED)) {
                            throw new SQLException("Blocked schedule identity changed");
                        }
                        stored.removeState(Schedule.ScheduleState.BLOCKED);
                        // This direct DAO update deliberately does not call
                        // ScheduleDao.save(), which can execute stock-alert
                        // observers before the outer SQLite COMMIT.
                        if (DB.schedules().update(stored) != 1) {
                            throw new SQLException("Could not atomically unblock schedule");
                        }
                        toRebuild.add(stored);
                    }
                }
            }
            return new AlertRemovalResult(current, toRebuild);
        });
        if (result == null) {
            return;
        }

        // SQLite has committed here. Re-read each schedule before creating
        // future occurrences: a new HIGH warning or a recycled patient/row ID
        // cannot authorize another patient's reminders after the COMMIT.
        // Attempt *every* regeneration even when one fails. A post-COMMIT
        // failure must not silently skip the remaining patient's schedules
        // or suppress the alert/schedule observers.
        RuntimeException postCommitFailure = null;
        for (Schedule snapshot : result.unblocked) {
            try {
                Schedule current = DB.schedules().findById(snapshot.getId());
                if (current == null || DB.schedules().refresh(current) != 1
                        || !PatientOwnerGuard.sameStableIdentity(
                                result.deleted.getPatient(), current.getPatient())
                        || current.getMedicine() == null
                        || snapshot.getMedicine() == null
                        || !snapshot.getMedicine().getId().equals(current.getMedicine().getId())
                        || current.hasState(Schedule.ScheduleState.BLOCKED)) {
                    continue;
                }
                Medicine currentMedicine = DB.medicines().findById(current.getMedicine().getId());
                if (currentMedicine == null
                        || !PatientOwnerGuard.sameStableIdentity(
                                result.deleted.getPatient(), currentMedicine.getPatient())
                        || !DB.alerts().findByMedicineAndLevel(
                                currentMedicine, Level.HIGH).isEmpty()) {
                    continue;
                }
                ScheduleUtils.instance().createEventInstances(CalendulaApp.getContext(), current);
            } catch (RuntimeException postCommitError) {
                LogUtil.e(TAG, "Committed HIGH alert release needs agenda recovery");
                if (postCommitFailure == null) {
                    postCommitFailure = postCommitError;
                }
            }
        }
        if (!result.unblocked.isEmpty()) {
            try {
                Agenda.instance().cleanReminders(CalendulaApp.getContext());
            } catch (RuntimeException postCommitError) {
                LogUtil.e(TAG, "Committed HIGH alert alarm cleanup needs retry");
                if (postCommitFailure == null) {
                    postCommitFailure = postCommitError;
                }
            }
            CalendulaApp.eventBus().post(PersistenceEvents.SCHEDULE_EVENT);
        }
        CalendulaApp.eventBus().post(PersistenceEvents.ALERT_EVENT);
        if (postCommitFailure != null) {
            throw new IllegalStateException(
                    "Alert removal committed; schedule recovery is incomplete",
                    postCommitFailure);
        }
    }

    private static final class AlertRemovalResult {
        final PatientAlert deleted;
        final List<Schedule> unblocked;

        AlertRemovalResult(PatientAlert deleted, List<Schedule> unblocked) {
            this.deleted = deleted;
            this.unblocked = unblocked;
        }
    }

    /** IDs may be recycled after restore; the nonblank stable code must match. */
    private static boolean sameStablePatient(Patient expected, Patient actual) {
        return expected != null && actual != null
                && expected.getId() != null && expected.getId() > 0L
                && expected.getId().equals(actual.getId())
                && expected.getCode() != null
                && !expected.getCode().trim().isEmpty()
                && expected.getCode().equals(actual.getCode());
    }

    /** Check clinical identity without exposing patient or medicine details. */
    static boolean sameAlertIdentity(PatientAlert snapshot, PatientAlert current) {
        return snapshot != null && current != null
                && snapshot.id() != null && snapshot.id().equals(current.id())
                && sameStablePatient(snapshot.getPatient(), current.getPatient())
                && snapshot.getMedicine() != null && current.getMedicine() != null
                && snapshot.getMedicine().getId() != null
                && snapshot.getMedicine().getId().equals(current.getMedicine().getId())
                && snapshot.getLevel() == current.getLevel()
                && java.util.Objects.equals(snapshot.getType(), current.getType());
    }

    public static void unblockSchedule(final Schedule schedule) {
        schedule.removeState(Schedule.ScheduleState.BLOCKED);
        DB.schedules().save(schedule);
        ScheduleUtils.instance().createEventInstances(CalendulaApp.getContext(), schedule);
        CalendulaApp.eventBus().post(PersistenceEvents.SCHEDULE_EVENT);
    }

    /**
     * Schedules with recycled numeric patient IDs must not be blocked by a
     * HIGH warning for a different account. Verify the medicine relationship
     * and durable owner codes, not only the SQLite foreign-key row numbers.
     */
    static boolean sameStableScheduleMedicine(Medicine medicine, Schedule selected) {
        return medicine != null && medicine.getId() != null && medicine.getId() > 0L
                && selected != null && selected.getId() != null && selected.getId() > 0L
                && selected.getMedicine() != null
                && medicine.getId().equals(selected.getMedicine().getId())
                && sameStablePatient(medicine.getPatient(), selected.getPatient())
                && sameStablePatient(medicine.getPatient(),
                        selected.getMedicine().getPatient());
    }

    /** Caller must already be inside the HIGH-alert SQLite transaction. */
    // SQL-only: call exclusively while the owning DB.transaction is open.
    // Medicine/allergy editing reuses this exact blocking policy so a
    // caller cannot commit a warning but leave its schedules runnable.
    static void blockSchedulesForMedicineInSql(final Medicine medicine)
            throws SQLException {
        final List<Schedule> schedules = DB.schedules().findByMedicine(medicine);
        final DateTime cutoff = DateTime.now();
        for (Schedule selected : schedules) {
            if (!sameStableScheduleMedicine(medicine, selected)) {
                throw new SQLException("Blocked schedule has invalid stable owner or medicine");
            }
            Schedule persisted = new Schedule();
            persisted.setId(selected.getId());
            if (DB.schedules().refresh(persisted) != 1
                    || !sameStableScheduleMedicine(medicine, persisted)
                    || !sameStablePatient(selected.getPatient(),
                            persisted.getPatient())) {
                throw new SQLException("Blocked schedule stable owner changed during alert");
            }
            persisted.addState(Schedule.ScheduleState.BLOCKED);
            if (DB.schedules().update(persisted) != 1) {
                throw new SQLException("Could not block selected schedule");
            }
            DB.eventInstances().removeByRefAndTime(persisted.getId(), cutoff);
        }
    }

}
