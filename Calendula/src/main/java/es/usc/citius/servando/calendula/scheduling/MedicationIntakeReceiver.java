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

package es.usc.citius.servando.calendula.scheduling;

import android.content.Context;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.activities.IntakeNotificationMgr;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.scheduling.model.AgendaUpdateListener;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventReminderReceiver;
import es.usc.citius.servando.calendula.scheduling.model.EventType;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

/**
 * Receives Medication Intake reminders and show a notification
 */
public class MedicationIntakeReceiver implements EventReminderReceiver, AgendaUpdateListener {

    @Override
    public void onEvent(Context context, EventReminder reminder) {
        boolean alarmLost = isLost(reminder.getDateTime());
        IntakeNotificationMgr.notify(context, reminder, alarmLost);
        CalendulaApp.eventBus().post(new Agenda.AgendaUpdatedEvent());
        if(alarmLost){
            Agenda.instance().removeReminder(context, reminder);
        }
    }

    @Override
    public boolean autoRepeat() {
        return true;
    }

    @Override
    public void onBeforeUpdate(Context context, LocalDate day) {
        DateTime dayStart = day.toDateTimeAtStartOfDay();
        DateTime dayEnd = dayStart.plusDays(1);
        DateTime from, to = dayEnd.plusDays(es.usc.citius.servando.calendula.persistence.ScheduleUtils.NEXT_DAYS_TO_SHOW);
        // delete items older than yesterday
        DB.eventInstances().removeOlderThan(EventType.MEDICATION_INTAKE, dayStart.minusDays(1));
        // delete items beyond tomorrow (only possible when changing date)
        DB.eventInstances().removeBeyond(EventType.MEDICATION_INTAKE, to);
        // check if today events are already generated
        if (DB.eventInstances().existBetween(EventType.MEDICATION_INTAKE, dayStart, dayEnd)) {
            from = dayEnd; // today events are generated
        }else{
            from = dayStart; // there are no events generated for today
        }
        for (Schedule s : DB.schedules().findAll()) {
            // generate events for each schedule and add them to the agenda
            Agenda.instance().addEvents(s.getEventsBetween(from, to));
        }
    }

    public static boolean isLost(DateTime t) {
        String delayMinutesStr = PreferenceUtils.getString(PreferenceKeys.SETTINGS_ALARM_REMINDER_WINDOW, "120");
        long window = Long.parseLong(delayMinutesStr);
        return t.plusMillis((int) window * 60 * 1000).isBeforeNow();
    }

}
