/*
 *    Calendula - An assistant for personal medication management.
 *    Copyright (C) 2016 CITIUS - USC
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

package es.usc.citius.servando.calendula.persistence;


import android.content.Context;

import org.joda.time.DateTime;
import org.joda.time.LocalTime;

import com.j256.ormlite.misc.TransactionManager;

import java.sql.SQLException;
import java.util.concurrent.Callable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.events.PersistenceEvents;
import es.usc.citius.servando.calendula.scheduling.Agenda;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventType;
import es.usc.citius.servando.calendula.scheduling.model.recur.DailyFixedTime;
import es.usc.citius.servando.calendula.util.stock.StockUpdater;
import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.alerts.StockAlertHandler;

/**
 * Utilities that simplify some tasks to do after creating or updating schedules
 */
public class ScheduleUtils {

    public static final String TAG = "ScheduleUtils";
    public static final int NEXT_DAYS_TO_SHOW = 1;
    private static final ScheduleUtils instance = new ScheduleUtils();

    private ScheduleUtils() {
    }

    public static ScheduleUtils instance() {
        return instance;
    }

    /**
     * Create event instances from now to midnight of
     * tomorrow ()
     *
     * @param s
     */
    public void createEventInstances(Context ctx, Schedule s) {
        Collection<EventInstance> events = getEventsFromTime(s, DateTime.now());
        Agenda.instance().addEvents(events);
        Agenda.instance().createReminders(ctx, events);
    }

    public void updateEventInstances(Context ctx, Schedule s) {
        DateTime now = DateTime.now();
        Collection<EventInstance> events = getEventsFromTime(s, now);
        // Instances that must be created
        Collection<EventInstance> toAdd = new ArrayList<>();
        // Instances that already exist and must be preserved
        Collection<DateTime> timesToPreserve = new ArrayList<>();
        for (EventInstance e : events) {
            if (!DB.eventInstances().exists(e.getType(), e.getTime(), e.getRef())) {
                toAdd.add(e);
            } else {
                DB.eventInstances().updateParams(e.getType(), e.getTime(), e.getRef(), e.getParams());
                timesToPreserve.add(e.getTime());
            }
        }
        DB.eventInstances().removeDistinctTime(EventType.MEDICATION_INTAKE, s.getId(), timesToPreserve, now);
        Agenda.instance().addEvents(toAdd);
        Agenda.instance().createReminders(ctx, toAdd);
        DB.eventInstances().fireEvent();
    }

    /** A cancelled or already completed dose is never a pending intake. */
    public static boolean isPendingIntake(EventInstance event) {
        return event != null && !event.completed() && !event.cancelled();
    }

    /**
     * The complete set of simultaneous doses is one SQLite unit of work:
     * neither earlier stock deductions nor intake flags may survive if a
     * later event in the batch fails. Android alarms and UI updates are
     * deliberately processed only after the outer transaction commits.
     *
     * @return the number of newly confirmed persisted doses, not the
     *         number selected before commit.
     */
    public int checkIntakeEvents(Context ctx, Patient patient, DateTime dateTime) {
        if (patient == null || patient.getId() == null || dateTime == null) {
            return 0;
        }
        final List<CommittedIntake> committed;
        final DateTime completedAt = DateTime.now();
        try {
            committed = TransactionManager.callInTransaction(
                    DB.helper().getConnectionSource(), (Callable<List<CommittedIntake>>) () -> {
                        List<CommittedIntake> changes = new ArrayList<>();
                        // Requery INSIDE the transaction; a previously selected
                        // event must not bypass the current pending/ownership check.
                        List<EventInstance> pending = DB.eventInstances().findPending(
                                EventType.MEDICATION_INTAKE, dateTime, patient);
                        for (EventInstance selected : pending) {
                            if (selected == null || selected.getId() == null) {
                                throw new SQLException("Pending dose has no persisted identity");
                            }
                        }
                        // Stable primary-key ordering makes retries and
                        // failure-injection behavior deterministic.
                        Collections.sort(pending,
                                (left, right) -> Long.compare(left.getId(), right.getId()));
                        for (EventInstance selected : pending) {
                            EventInstance stored = DB.eventInstances().findById(selected.getId());
                            if (stored == null
                                    || stored.getType() != EventType.MEDICATION_INTAKE
                                    || stored.getPatient() == null
                                    || !patient.getId().equals(stored.getPatient().getId())
                                    || !dateTime.equals(stored.getTime())
                                    || !isPendingIntake(stored)) {
                                throw new SQLException("Pending dose changed during confirmation");
                            }
                            changes.add(persistIntakeTransition(stored, true, completedAt));
                        }
                        return changes;
                    });
        } catch (SQLException e) {
            throw new IllegalStateException("Could not atomically confirm all medication doses", e);
        }

        for (CommittedIntake intake : committed) {
            publishCommittedIntake(ctx, intake, true);
        }
        if (!committed.isEmpty()) {
            fireCommittedIntakeEvent();
        }
        return committed.size();
    }

    public void delayIntake(Context ctx, Patient patient, DateTime dateTime, int delay) {
        Agenda.instance().onDelayReminder(ctx, EventType.MEDICATION_INTAKE, dateTime, patient, delay * 60);
    }

    /** A committed event/stock pair used exclusively after SQL success. */
    private static final class CommittedIntake {
        final EventInstance event;
        final boolean stockChanged;

        CommittedIntake(EventInstance event, boolean stockChanged) {
            this.event = event;
            this.stockChanged = stockChanged;
        }
    }

    /** Must be invoked inside the transaction that owns the intake update. */
    private CommittedIntake persistIntakeTransition(
            EventInstance stored, boolean completed, DateTime completedAt) throws SQLException {
        if (stored == null || stored.getType() != EventType.MEDICATION_INTAKE
                || stored.getId() == null || stored.cancelled()) {
            throw new SQLException("Invalid or cancelled medication intake");
        }
        if (stored.completed() == completed) {
            throw new SQLException("Intake transition was already applied");
        }
        boolean previouslyCompleted = stored.completed();
        stored.setCompleted(completed);
        stored.setCompletedAt(completedAt);
        boolean stockChanged = StockUpdater.applyStockForTransition(stored, previouslyCompleted);
        if (DB.eventInstances().update(stored) != 1) {
            throw new SQLException("Expected one intake row to change");
        }
        return new CommittedIntake(stored, stockChanged);
    }

    /**
     * Single-dose confirmation/undo retains idempotent false for repeated
     * requests. Its stock and event updates use the same transaction as
     * the all-doses path.
     */
    public boolean setIntakeCompleted(Context ctx, EventInstance event, boolean completed) {
        if (event == null || event.getId() == null
                || event.getType() != EventType.MEDICATION_INTAKE) {
            throw new IllegalArgumentException("A persisted medication intake is required");
        }
        final DateTime completedAt = completed ? DateTime.now() : null;
        final CommittedIntake committed;
        try {
            committed = TransactionManager.callInTransaction(
                    DB.helper().getConnectionSource(), (Callable<CommittedIntake>) () -> {
                        EventInstance stored = DB.eventInstances().findById(event.getId());
                        if (stored == null || stored.getType() != EventType.MEDICATION_INTAKE) {
                            throw new SQLException("Intake disappeared during confirmation");
                        }
                        // Re-query by ID is necessary but not sufficient:
                        // a stale UI event can now refer to another patient's
                        // intake or a changed prescription/time. Never mutate
                        // its stock or completion flag on that old identity.
                        if (!java.util.Objects.equals(event.getRef(), stored.getRef())
                                || !java.util.Objects.equals(event.getTime(), stored.getTime())
                                || !samePatientIdentity(event.getPatient(), stored.getPatient())) {
                            throw new SQLException("Intake identity changed during confirmation");
                        }
                        if (stored.cancelled()) {
                            throw new SQLException("Cancelled medication cannot be confirmed or undone");
                        }
                        if (stored.completed() == completed) {
                            return null;
                        }
                        return persistIntakeTransition(stored, completed, completedAt);
                    });
        } catch (SQLException e) {
            throw new IllegalStateException("Could not atomically persist medication intake", e);
        }
        if (committed == null) {
            return false;
        }

        // Only a committed transaction can alter a caller-owned instance.
        event.setCompleted(completed);
        event.setCompletedAt(completedAt);
        publishCommittedIntake(ctx, committed, completed);
        fireCommittedIntakeEvent();
        return true;
    }

    /**
     * Null patient references are allowed only for matching legacy unassigned
     * events; two transient patient objects with missing IDs do not establish
     * identity. Persisted patient IDs must be equal and strictly positive.
     */
    private static boolean samePatientIdentity(Patient caller, Patient persisted) {
        if (caller == null || persisted == null) {
            return caller == null && persisted == null;
        }
        return caller.getId() != null && caller.getId() > 0
                && caller.getId().equals(persisted.getId());
    }

    /** Never perform platform side effects while a SQLite transaction is open. */
    private void publishCommittedIntake(Context ctx, CommittedIntake intake, boolean completed) {
        EventInstance event = intake.event;
        if (intake.stockChanged) {
            try {
                Schedule schedule = DB.schedules().findById(event.getRef());
                if (schedule != null && schedule.getMedicine() != null) {
                    StockAlertHandler.checkStockAlerts(schedule.getMedicine());
                }
                DB.medicines().fireEvent();
            } catch (RuntimeException alertError) {
                LogUtil.e(TAG, "Post-commit medicine stock alert failed", alertError);
            }
        }
        try {
            if (completed) {
                Agenda.instance().cleanReminderIfPossible(
                        ctx, event.getPatient(), event.getType(), event.getTime());
            } else if (!Agenda.instance().createReminders(ctx, Arrays.asList(event))) {
                LogUtil.e(TAG, "Post-commit reminder recovery could not be scheduled");
            }
        } catch (RuntimeException schedulingError) {
            LogUtil.e(TAG, "Post-commit reminder reconciliation failed", schedulingError);
        }
    }

    private void fireCommittedIntakeEvent() {
        try {
            DB.eventInstances().fireEvent();
        } catch (RuntimeException listenerError) {
            LogUtil.e(TAG, "Post-commit intake UI update failed", listenerError);
        }
    }

    public List<EventInstance> intakeEvents(Patient p, DateTime dateTime) {
        return DB.eventInstances().find(EventType.MEDICATION_INTAKE, dateTime, p);
    }

    public String getIntakeTitle(Patient p, LocalTime time, Context c) {
        Routine routine = DB.routines().findByPatientAndTime(p, time);
        if (routine != null && routine.getName() != null) {
            return routine.getName();
        } else {
            return c.getString(R.string.dosage_string_time_of_day, p.getName(), ",", time.toString("HH:mm"));
        }
    }

    public void removeSchedule(Context c, Schedule s, boolean fireEvent) {
        // remove schedule events and reminders
        removeScheduleEvents(c, s, false);
        // remove the schedule
        DB.schedules().remove(s);
        // remove unlinked auto-created routines
        removeUnlinkedAutoRoutines(false);
        if (fireEvent) {
            DB.schedules().fireEvent();
        }
    }

    public void removeScheduleEvents(Context c, Schedule s, boolean fireEvent){
        // remove event instances
        DB.eventInstances().removeByRef(s.getId());
        // clean reminders
        Agenda.instance().cleanReminders(c);
    }

    public void removeFutureScheduleEvents(Context c, Schedule s, boolean fireEvent){
        // remove event instances
        DB.eventInstances().removeByRefAndTime(s.getId(),DateTime.now());
        // clean reminders
        Agenda.instance().cleanReminders(c);
    }

    public void onUpdateRoutine(Context context, Routine r) {
        List<Schedule> schedules = DB.schedules().findAll(r.getPatient());
        List<Schedule> toUpdate = new ArrayList<>();
        for(Schedule s : schedules){
            if(s.getRecur().hasDailyFixedTimes()){
                for(DailyFixedTime d : s.getRecur().getDailyFixedTimes()){
                    if(d.getReferenceType().equals(DailyFixedTime.ReferenceType.ROUTINE)
                            && d.getReference().equals(r.getId())){
                        if(!toUpdate.contains(s)){
                            toUpdate.add(s);
                        }
                    }
                }
            }
        }

        for(Schedule s : toUpdate){
            updateEventInstances(context, s);
        }
    }

    public int schedulesLinkedTo(Routine r) {
        return  getSchedulesLinkedTo(r).size();
    }

    public void onDeleteRoutine(Context c, Routine r) {
        List<Schedule> linkedTo = getSchedulesLinkedTo(r);
        if(!linkedTo.isEmpty()) {
            for (Schedule s : linkedTo) {
                removeSchedule(c, s, false);
            }
            Agenda.instance().cleanReminders(c);
            CalendulaApp.eventBus().post(PersistenceEvents.SCHEDULE_EVENT);
        }
    }

    private List<Schedule> getSchedulesLinkedTo(Routine r) {
        List<Schedule> schedules = DB.schedules().findAll(r.getPatient());
        List<Schedule> linked = new ArrayList<>();
        for(Schedule s : schedules){
            if(s.getRecur().hasDailyFixedTimes()){
                for(DailyFixedTime d : s.getRecur().getDailyFixedTimes()){
                    if(d.getReferenceType().equals(DailyFixedTime.ReferenceType.ROUTINE)
                            && d.getReference().equals(r.getId())){
                        if(!linked.contains(s)){
                            linked.add(s);
                        }
                    }
                }
            }
        }
        return linked;
    }

    public void removeUnlinkedAutoRoutines(boolean fireEvent) {
        boolean somethingRemoved = false;
        for (Routine r : DB.routines().findWithoutName()) {
            int linked = ScheduleUtils.instance().schedulesLinkedTo(r);
            if (linked <= 0) {
                DB.routines().remove(r);
                somethingRemoved = true;
            }
        }
        if (fireEvent && somethingRemoved) {
            DB.routines().fireEvent();
        }
    }

    private Collection<EventInstance> getEventsFromTime(Schedule s, DateTime from) {
        DateTime to = from.plusDays(1 + NEXT_DAYS_TO_SHOW).withTimeAtStartOfDay();
        return s.getEventsBetween(from, to);
    }
}
