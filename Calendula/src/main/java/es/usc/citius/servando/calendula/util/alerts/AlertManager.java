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
import es.usc.citius.servando.calendula.persistence.PatientAlert;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.persistence.ScheduleUtils;
import es.usc.citius.servando.calendula.util.LogUtil;

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
                    if (alert.getPatient() == null || alert.getPatient().getId() == null
                            || alert.getPatient().getId() <= 0L
                            || alert.getMedicine() == null || alert.getMedicine().getId() == null
                            || alert.getMedicine().getId() <= 0L
                            || DB.patients().findById(alert.getPatient().getId()) == null) {
                        throw new SQLException("HIGH alert is missing a persisted owner or medicine");
                    }
                    verifiedMedicine = DB.medicines().findById(alert.getMedicine().getId());
                    if (verifiedMedicine == null || verifiedMedicine.getPatient() == null
                            || verifiedMedicine.getPatient().getId() == null
                            || !alert.getPatient().getId().equals(
                                    verifiedMedicine.getPatient().getId())) {
                        throw new SQLException("HIGH alert patient differs from medicine owner");
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

        DB.alerts().remove(alert);
        //if alertlevel was high, check if we need to unblock schedules
        if (alert.getLevel() == Level.HIGH) {

            final List<Schedule> blocked = DB.schedules()
                    .findByMedicineAndState(alert.getMedicine(), Schedule.ScheduleState.BLOCKED);
            if (blocked.size() > 0) {
                final List<PatientAlert> alerts = DB.alerts()
                        .findByMedicineAndLevel(alert.getMedicine(), Level.HIGH);
                if (alerts.size() == 0) {
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
                    || !selected.getPatient().getId().equals(medicine.getPatient().getId())) {
                throw new SQLException("Blocked schedule has invalid patient ownership");
            }
            Schedule persisted = new Schedule();
            persisted.setId(selected.getId());
            if (DB.schedules().refresh(persisted) != 1 || persisted.getPatient() == null
                    || !selected.getPatient().getId().equals(
                            persisted.getPatient().getId())) {
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
