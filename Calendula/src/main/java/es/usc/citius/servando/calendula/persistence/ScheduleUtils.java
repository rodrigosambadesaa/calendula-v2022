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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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

    public void checkIntakeEvents(Context ctx, Patient patient, DateTime dateTime) {
        boolean fireEvent=false;
        for (EventInstance e : ScheduleUtils.instance().intakeEvents(patient, dateTime)) {
            if (!e.completed()) {
                setIntakeCompleted(ctx, e, true);
                fireEvent=true;
            }
        }
         if (fireEvent) {
            DB.eventInstances().fireEvent();
        }
    }

    public void delayIntake(Context ctx, Patient patient, DateTime dateTime, int delay) {
        Agenda.instance().onDelayReminder(ctx, EventType.MEDICATION_INTAKE, dateTime, patient, delay * 60);
    }

    public void setIntakeCompleted(Context ctx, EventInstance event, boolean completed) {
        event.setCompleted(completed);
        event.setCompletedAt(completed ? DateTime.now() : null);
        StockUpdater.updateStockForIntake(event,true);
        DB.eventInstances().save(event);
        if(completed) {
            Agenda.instance().cleanReminderIfPossible(ctx, event.getPatient(), event.getType(), event.getTime());
        }else{
            Agenda.instance().createReminders(ctx, Arrays.asList(event));
        }
        DB.eventInstances().fireEvent();
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
