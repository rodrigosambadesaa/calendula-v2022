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
                Medicine verifiedMedicine = null;
                if (high) {
                    // Never let a stale or mislinked HIGH alert mutate another
                    // patient's schedules. The authoritative medicine owner must
                    // match the persisted alert patient before ANY SQL write.
                    if (!PatientOwnerGuard.hasStableIdentity(alert.getPatient())
                            || alert.getMedicine() == null || alert.getMedicine().getId() == null
                            || alert.getMedicine().getId() <= 0L) {
                        throw new SQLException("HIGH alert is missing a stable owner or medicine");
                    }
                    Patient persistedOwner = DB.patients().findById(alert.getPatient().getId());
                    verifiedMedicine = DB.medicines().findById(alert.getMedicine().getId());
                    // A restored SQLite database can reuse the exact same row
                    // numbers for a different patient. Verify the stable code
                    // both for the stored patient and the stored medicine.
                    if (!PatientOwnerGuard.sameStableIdentity(
                                alert.getPatient(), persistedOwner)
                            || verifiedMedicine == null
                            || !PatientOwnerGuard.sameStableIdentity(
                                alert.getPatient(), verifiedMedicine.getPatient())) {
                        throw new SQLException("HIGH alert patient differs from persisted owner");
                    }
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
        // A UI/worker may hold an old alert object. Do not let that stale
        // object delete a replacement or a clinical alert reassigned to
        // another patient, medicine, or severity.
        PatientAlert persisted = (PatientAlert) DB.transaction(() -> {
            PatientAlert current = DB.alerts().findById(alert.id());
            if (current == null) {
                return null; // Already removed; never unblock anything.
            }
            // Resolve the medicine and patient on their own DAO connections,
            // not only through possibly shallow foreign-auto-refreshed alert
            // references. A deleted/recreated medicine ID is never authority.
            Medicine linked = current.getMedicine() == null
                    || current.getMedicine().getId() == null ? null
                    : DB.medicines().findById(current.getMedicine().getId());
            Patient persistedOwner = current.getPatient() == null
                    || current.getPatient().getId() == null ? null
                    : DB.patients().findById(current.getPatient().getId());
            if (!sameAlertIdentity(alert, current)
                    || !PatientOwnerGuard.sameStableIdentity(
                            alert.getPatient(), persistedOwner)
                    || linked == null
                    || !PatientOwnerGuard.sameStableIdentity(
                            alert.getPatient(), linked.getPatient())) {
                throw new IllegalStateException("Alert identity changed before deletion");
            }
            if (DB.alerts().deleteById(current.id()) != 1) {
                throw new SQLException("Alert deletion did not remove exactly one row");
            }
            return current;
        });
        if (persisted == null) {
            return;
        }

        // The remaining HIGH alerts must be checked AFTER the committed
        // deletion. Reuse the authoritative persisted owner/medicine rather
        // than an untrusted callback snapshot.
        if (persisted.getLevel() == Level.HIGH) {
            final List<Schedule> blocked = DB.schedules().findByMedicineAndState(
                    persisted.getMedicine(), Schedule.ScheduleState.BLOCKED);
            if (!blocked.isEmpty()) {
                final List<PatientAlert> alerts = DB.alerts().findByMedicineAndLevel(
                        persisted.getMedicine(), Level.HIGH);
                if (alerts.isEmpty()) {
                    DB.transaction(new Callable<Object>() {
                        @Override
                        public Object call() throws Exception {
                            for (Schedule schedule : blocked) {
                                unblockSchedule(schedule);
                            }
                            return null;
                        }
                    });
                }
            }
        }
    }

    /** Check clinical identity without exposing patient or medicine details. */
    static boolean sameAlertIdentity(PatientAlert snapshot, PatientAlert current) {
        return snapshot != null && current != null
                && snapshot.id() != null && snapshot.id().equals(current.id())
                && PatientOwnerGuard.sameStableIdentity(
                        snapshot.getPatient(), current.getPatient())
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

    /** Caller must already be inside the HIGH-alert SQLite transaction. */
    private static void blockSchedulesForMedicineInSql(final Medicine medicine)
            throws SQLException {
        final List<Schedule> schedules = DB.schedules().findByMedicine(medicine);
        final DateTime cutoff = DateTime.now();
        for (Schedule selected : schedules) {
            if (selected == null || selected.getId() == null
                    || selected.getPatient() == null
                    || medicine == null || medicine.getPatient() == null
                    || !PatientOwnerGuard.sameStableIdentity(
                            selected.getPatient(), medicine.getPatient())) {
                throw new SQLException("Blocked schedule has invalid patient ownership");
            }
            Schedule persisted = new Schedule();
            persisted.setId(selected.getId());
            if (DB.schedules().refresh(persisted) != 1
                    || !PatientOwnerGuard.sameStableIdentity(
                            selected.getPatient(), persisted.getPatient())) {
                throw new SQLException("Blocked schedule owner changed during alert");
            }
            persisted.addState(Schedule.ScheduleState.BLOCKED);
            if (DB.schedules().update(persisted) != 1) {
                throw new SQLException("Could not block selected schedule");
            }
            DB.eventInstances().removeByRefAndTime(persisted.getId(), cutoff);
        }
    }

}
