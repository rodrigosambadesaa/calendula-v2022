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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import com.j256.ormlite.misc.TransactionManager;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.activities.IntakeNotificationMgr;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.ScheduleUtils;
import es.usc.citius.servando.calendula.scheduling.model.AgendaUpdateListener;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventReminderReceiver;
import es.usc.citius.servando.calendula.scheduling.model.EventType;
import es.usc.citius.servando.calendula.util.IntentParams;
import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;


public class Agenda {

    public static final String TAG = "AgendaMgr";
    private static final Agenda instance = new Agenda();
    private DateTimeFormatter localDateFmt = ISODateTimeFormat.basicDate();
    private DateTimeFormatter timeFmt = ISODateTimeFormat.basicDateTime();
    private Map<EventType, EventReminderReceiver> receivers = new HashMap<>();
    private List<AgendaUpdateListener> updateListeners = new ArrayList<>();

    private Agenda() {
    }

    public static final Agenda instance() {
        return instance;
    }

    public void registerReminderReceiver(EventType type, EventReminderReceiver receiver) {
        receivers.put(type, receiver);
    }

    public void registerListener(AgendaUpdateListener listener) {
        updateListeners.add(listener);
    }

    public void addEvents(Collection<EventInstance> evts) {
        DB.eventInstances().saveAll(evts);
    }

    public void createReminders(final Context context, final Collection<EventInstance> intakes) {
        try {
            TransactionManager.callInTransaction(DB.helper().getConnectionSource(), new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    for (EventInstance e : intakes) {
                        if (!DB.eventReminders().exists(e.getType(), e.getTime(), e.getPatient())) {
                            LogUtil.d(TAG, "Reminder needs to be created for " + e.getType() + " at " + e.getTime().toString());
                            if (canBeScheduled(e.getTime())) {
                                EventReminder reminder = new EventReminder(e.getTime(), e.getType());
                                reminder.setNextTime(e.getTime());
                                reminder.setPatient(e.getPatient());
                                reminder.setAutoRepeat(getAutoRepeat(e.getType()));
                                DB.eventReminders().save(reminder);
                                setAlarm(context, reminder);
                            } else {
                                LogUtil.d(TAG, "Event at can not be scheduled");
                            }
                        } else {
                            LogUtil.d(TAG, "Reminder already exist for " + e.getType() + " at " + e.getTime().toString());
                        }
                    }
                    cleanReminders(context);
                    return null;
                }
            });
        } catch (SQLException e) {
            LogUtil.e(TAG, "Error creating reminders ", e);
        }
    }

    public void createReminders(Context context) {
        createReminders(context, DB.eventInstances().findAll());
    }

    public void updateAllAlarms(Context context) {
        List<EventReminder> eventReminders = DB.eventReminders().findAll();
        LogUtil.d(TAG, "EventReminders: " + eventReminders.size());
        for (EventReminder e : eventReminders) {
            setAlarm(context, e);
        }
    }

    public void setAlarm(Context context, EventReminder reminder) {
        // build a bundle with the alarm params
        Bundle alarmParams = new Bundle();
        alarmParams.putString("action", "reminder");
        alarmParams.putLong("reminder_id", reminder.getId());
        // millis to set the alarm
        DateTime dateTime = reminder.getNextTime();
        LogUtil.d(TAG, "Creating reminder for : " + reminder.getEventType() + " at " + dateTime.toString("dd/MM HH:mm"));
        // intent we should receive on millis
        PendingIntent pendingIntent = getReminderIntent(context, reminder);
        // set the alarm
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            if (Build.VERSION.SDK_INT >= 23) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dateTime.getMillis(), pendingIntent);
                LogUtil.d(TAG, "Calling alarm manager");
            } else if (Build.VERSION.SDK_INT >= 19) {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, dateTime.getMillis(), pendingIntent);
                LogUtil.d(TAG, "Calling alarm manager");
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, dateTime.getMillis(), pendingIntent);
                LogUtil.d(TAG, "Calling alarm manager");
            }
        }
    }

    // called from broadcast receiver
    public void onReceiveAlarm(Context ctx, Long reminderId) {
        // get the r from db
        EventReminder r = DB.eventReminders().findById(reminderId);
        if (r != null) {
            LogUtil.d(TAG, "Received reminder: " + r.getId() + ", " + r.getDateTime());
            sendReminderToReceiver(ctx, r);
        } else {
            LogUtil.w(TAG, "Reminder with id " + reminderId + " not found");
        }
    }

    public List<EventInstance> getEvents(EventType type, DateTime dateTime) {
        return DB.eventInstances().findByTypeAndTime(type, dateTime);
    }

    /*
    * Whether an alarm for a specific time can be scheduled or not based on
    * the alarm time and the alarm reminder window defined by the user. Alarm time plus
    * alarm window must be in the future to allow alarm scheduling, and must also be today
    */
    public boolean canBeScheduled(DateTime t) {
        String delayMinutesStr = PreferenceUtils.getString(PreferenceKeys.SETTINGS_ALARM_REMINDER_WINDOW, "60");
        int window = (int) Long.parseLong(delayMinutesStr);
        DateTime midNight = DateTime.now().withTimeAtStartOfDay().plusDays(1);
        LogUtil.d(TAG, "T: " + t.toString() + ", window: " + window + ", midNight: " + midNight.toString());
        return t.plusMinutes(window).isAfterNow() && !midNight.isBefore(t);
    }

    public boolean shouldReschedule(EventReminder e, DateTime at) {
        String delayMinutesStr = PreferenceUtils.getString(PreferenceKeys.SETTINGS_ALARM_REMINDER_WINDOW, "60");
        int window = (int) Long.parseLong(delayMinutesStr);
        return at.isAfterNow() && !at.isAfter(e.getDateTime().plusMinutes(window + 5));
    }

    public boolean isInIntakeWindow(DateTime t) {
        String delayMinutesStr = PreferenceUtils.getString(PreferenceKeys.SETTINGS_ALARM_REMINDER_WINDOW, "60");
        long window = Long.parseLong(delayMinutesStr);
        DateTime now = DateTime.now();
        return t.isBefore(now) && t.plusMillis((int) window * 60 * 1000).isAfter(now);
    }

    public void setDailyUpdateAlarm(Context ctx) {
        // intent our receiver will receive
        Intent intent = new Intent(ctx, AlarmReceiver.class);
        intent.putExtra(IntentParams.EXTRA_ACTION, IntentParams.ACTION_DAILY_UPDATE);
        PendingIntent dailyAlarm = PendingIntent.getBroadcast(ctx, IntentParams.DAILY_UPDATE_ID, intent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarmManager = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    new LocalTime(0, 0).toDateTimeToday().getMillis(),
                    AlarmManager.INTERVAL_DAY, dailyAlarm
            );
        }
    }

    public void start(final Context context) {
        setDailyUpdateAlarm(context);
        onDailyUpdate(context);
    }


    public void onDailyUpdate(final Context context) {
        final LocalDate today = LocalDate.now();
        if (needsToBeUpdated(today)) {
            // Start transaction
            try {
                TransactionManager.callInTransaction(DB.helper().getConnectionSource(), new Callable<Object>() {
                    @Override
                    public Object call() throws Exception {
                        // remove old reminders
                        removeRemindersBefore(today.minusDays(1));
                        // fire before update event
                        onBeforeUpdate(context, today);
                        // create reminders for events
                        Agenda.instance().createReminders(context);
                        // Save last date to prefs
                        PreferenceUtils.edit()
                                .putString(PreferenceKeys.AGENDA_LAST_UPDATED.key(), today.toString(localDateFmt))
                                .apply();
                        return null;
                    }
                });
            } catch (SQLException e) {
                LogUtil.e(TAG, "Error updating agenda", e);
            }
            // Update alarms
            Agenda.instance().updateAllAlarms(context);
            CalendulaApp.eventBus().post(new AgendaUpdatedEvent());
        } else {
            LogUtil.d(TAG, "No need to update daily schedule (" + DB.eventInstances().count() + " items found for today)");
            logReminders();
        }
    }

    public void cleanReminderIfPossible(Context context, Patient patient, EventType type, DateTime time) {
        // look for not incomplete events linked to this reminder
        if (!DB.eventInstances().exists(type, time, patient, false)) {
            EventReminder r = DB.eventReminders().findBy(type, time, patient);
            IntakeNotificationMgr.cancel(context, r);
            DB.eventReminders().remove(r);
        }

    }

    public void cleanReminders(Context context) {
        List<EventReminder> eventReminders = DB.eventReminders().findAll();
        for (EventReminder r : eventReminders) {
            cleanReminderIfPossible(context, r.getPatient(), r.getEventType(), r.getDateTime());
        }
        logReminders();
    }

    public void deleteAllReminders(Context context) {
        List<EventReminder> eventReminders = DB.eventReminders().findAll();
        for (EventReminder r : eventReminders) {
            IntakeNotificationMgr.cancel(context, r);
            DB.eventReminders().remove(r);
        }
        logReminders();
    }

    public void onDelayReminder(Context context, EventType type, DateTime time, Patient p, int delay) {
        EventReminder reminder = DB.eventReminders().findBy(type, time, p);
        delayReminder(context, reminder, delay);
    }


    public void confirmReminder(Context context, Long reminderId) {
        EventReminder reminder = DB.eventReminders().findById(reminderId);
        if (reminder != null) {
            IntakeNotificationMgr.cancel(context, reminder);
            DB.eventInstances().confirm(reminder.getEventType(),
                    reminder.getDateTime(),
                    reminder.getPatient(),
                    DateTime.now());
            // cancel the alarm and remove the reminder
            cancelAlarm(context, reminder);
            DB.eventReminders().remove(reminder);
        }
    }


    public void cancelReminder(Context context, Long reminderId) {
        EventReminder reminder = DB.eventReminders().findById(reminderId);
        if (reminder != null) {
            IntakeNotificationMgr.cancel(context, reminder);
            removeReminder(context, reminder);
        }
    }

    public void removeReminder(Context context, EventReminder reminder) {
        DB.eventInstances().cancelUncompleted(reminder.getEventType(),
                reminder.getDateTime(),
                reminder.getPatient(),
                DateTime.now());
        // cancel the alarm and remove the reminder
        cancelAlarm(context, reminder);
        DB.eventReminders().remove(reminder);
    }

    public void delayReminder(Context context, Long reminderId) {
        delayReminder(context, DB.eventReminders().findById(reminderId), null);
    }

    public void delayReminder(Context context, EventReminder reminder, Integer delay) {
        if (reminder != null) {
            IntakeNotificationMgr.cancel(context, reminder);
            reminder.setNextTime(DateTime.now().plusSeconds(delay != null ? delay : repeatFreqSeconds()));
            LogUtil.d(TAG, "Updating reminder");
            DB.eventReminders().save(reminder);
            setAlarm(context, reminder);
        }
    }

    private void logReminders() {
        LogUtil.d(TAG, "Current reminders -------------------------------");
        for (EventReminder r : DB.eventReminders().findAll()) {
            LogUtil.d(TAG, r.getId() + "\t"
                    + r.getEventType() + "\t"
                    + r.getPatient().getId() + "\t"
                    + r.getDateTime().toString(ISODateTimeFormat.dateHourMinute()) + "\t"
                    + r.getNextTime().toString(ISODateTimeFormat.dateHourMinute())
            );

        }
        LogUtil.d(TAG, "-------------------------------------------------");

    }

    private void onBeforeUpdate(Context context, LocalDate today) {
        for (AgendaUpdateListener l : updateListeners) {
            l.onBeforeUpdate(context, today);
        }
    }

    private boolean needsToBeUpdated(LocalDate today) {
        String lastDate = PreferenceUtils.getString(PreferenceKeys.AGENDA_LAST_UPDATED, null);
        LocalDate lastUpdated;
        if (lastDate != null) {
            lastUpdated = localDateFmt.parseLocalDate(lastDate);
            if (today.equals(lastUpdated)) {
                return false;
            }
        }
        return true;
    }

    private void removeRemindersBefore(LocalDate date) {
        DB.eventReminders().removeOlderThan(date.toDateTimeAtStartOfDay());
    }

    private void sendReminderToReceiver(Context ctx, EventReminder r) {
        boolean eventExist = DB.eventInstances().exists(r.getEventType(), r.getDateTime());
        LogUtil.d(TAG, "There are events to remind!");
        if (eventExist) {
            // get the appropriate receiver
            EventReminderReceiver receiver = receivers.get(r.getEventType());
            // and send the event to it
            LogUtil.d(TAG, "Sending event to " + r.getEventType() + " receiver");
            receiver.onEvent(ctx, r);
            // auto repeat
            if (r.autoRepeat()) {
                LogUtil.d(TAG, "Auto repeat enabled, try to reschedule repeat");
                DateTime now = DateTime.now();
                DateTime nextTime = now.plusSeconds(repeatFreqSeconds());
                if (shouldReschedule(r, nextTime)) {
                    r.setNextTime(nextTime);
                    LogUtil.d(TAG, "Updating reminder to " + nextTime.toString(timeFmt));
                    DB.eventReminders().save(r);
                    setAlarm(ctx, r);
                } else {
                    LogUtil.d(TAG, "Event con not be rescheduled");
                }
            }
        } else {
            // remove the reminder
            LogUtil.d(TAG, "Cancelling reminder with id " + r.getId());
            cancelAlarm(ctx, r);
            DB.eventReminders().remove(r);
        }
    }

    private boolean getAutoRepeat(EventType type) {
        if (receivers.containsKey(type)) {
            return receivers.get(type).autoRepeat();
        }
        return false;
    }

    private PendingIntent getReminderIntent(Context context, EventReminder reminder) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra(IntentParams.EXTRA_ACTION, IntentParams.ACTION_ALARM_REMINDER);
        intent.putExtra(IntentParams.EXTRA_REMINDER_ID, reminder.getId());
        return PendingIntent.getBroadcast(
                context,
                reminder.hashCode(),
                intent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void cancelAlarm(Context context, EventReminder reminder) {
        PendingIntent pendingIntent = getReminderIntent(context, reminder);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
        }
    }

    private int repeatFreqSeconds() {
        String delayMinutesStr = PreferenceUtils.getString(PreferenceKeys.SETTINGS_ALARM_REPEAT_FREQUENCY, "15");
        return Integer.parseInt(delayMinutesStr) * 60;
    }

    public static class AgendaUpdatedEvent {

    }
}
