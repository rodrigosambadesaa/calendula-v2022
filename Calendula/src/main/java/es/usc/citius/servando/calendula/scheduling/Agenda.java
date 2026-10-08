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
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import com.j256.ormlite.misc.TransactionManager;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
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
import es.usc.citius.servando.calendula.util.PendingIntentFlags;
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

    public boolean createReminders(final Context context, final Collection<EventInstance> intakes) {
        return createRemindersInternal(context, intakes, true);
    }

    /** Only persist rows while the enclosing daily SQLite transaction is open. */
    boolean createRemindersForDailyUpdate(final Context context) {
        return createRemindersInternal(context, DB.eventInstances().findAll(), false);
    }

    private boolean createRemindersInternal(final Context context,
                                            final Collection<EventInstance> intakes,
                                            final boolean allowPlatformEffects) {
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
                                if (allowPlatformEffects) {
                                    setAlarm(context, reminder);
                                }
                            } else {
                                LogUtil.d(TAG, "Event at can not be scheduled");
                            }
                        } else {
                            LogUtil.d(TAG, "Reminder already exist for " + e.getType() + " at " + e.getTime().toString());
                        }
                    }
                    // Platform alarm cancellation is also external to SQLite.
                    // During daily rebuild, postpone it until outer commit.
                    if (allowPlatformEffects) {
                        cleanReminders(context);
                    }
                    return null;
                }
            });
            return true;
        } catch (SQLException e) {
            LogUtil.e(TAG, "Error creating reminders ", e);
            // An outer agenda transaction must not publish a completed-day
            // marker when this nested reminder transaction has rolled back.
            return false;
        }
    }

    public boolean createReminders(Context context) {
        return createReminders(context, DB.eventInstances().findAll());
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
        LogUtil.d(TAG, "Creating scheduled reminder");
        // intent we should receive on millis
        PendingIntent pendingIntent = getReminderIntent(context, reminder);
        // set the alarm
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            // An older release used a mutable hash for PendingIntent identity.
            // Retire the matching old request before scheduling the stable ID.
            cancelLegacyAlarm(context, reminder, alarmManager);
            scheduleAlarm(alarmManager, dateTime.getMillis(), pendingIntent);
        }
    }

    /**
     * Schedule reminders despite a race in which the user revokes special exact-alarm
     * access between canScheduleExactAlarms() and setExactAndAllowWhileIdle().
     * An inexact reminder is safer than dropping the reminder or crashing the app.
     */
    static void scheduleAlarm(AlarmManager alarmManager, long triggerAtMillis,
                              PendingIntent pendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            LogUtil.w(TAG, "Exact alarm access unavailable; scheduled inexact reminder fallback");
            return;
        }
        try {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            LogUtil.d(TAG, "Calling alarm manager");
        } catch (SecurityException e) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                throw e;
            }
            // Special exact-alarm access can change after the initial check.
            LogUtil.w(TAG, "Exact alarm access changed during scheduling; using inexact fallback");
            alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
        }
    }

    // called from broadcast receiver
    public void onReceiveAlarm(Context ctx, Long reminderId) {
        // get the r from db
        EventReminder r = DB.eventReminders().findById(reminderId);
        if (r != null) {
            LogUtil.d(TAG, "Received scheduled reminder");
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
        int window = ReminderTiming.windowMinutes();
        DateTime midNight = DateTime.now().withTimeAtStartOfDay().plusDays(1);
        LogUtil.d(TAG, "T: " + t.toString() + ", window: " + window + ", midNight: " + midNight.toString());
        return t.plusMinutes(window).isAfterNow() && !midNight.isBefore(t);
    }

    public boolean shouldReschedule(EventReminder e, DateTime at) {
        int window = ReminderTiming.windowMinutes();
        return at.isAfterNow() && !at.isAfter(e.getDateTime().plusMinutes(window + 5));
    }

    public boolean isInIntakeWindow(DateTime t) {
        int window = ReminderTiming.windowMinutes();
        DateTime now = DateTime.now();
        return t.isBefore(now) && t.plusMinutes(window).isAfter(now);
    }

    /**
     * The first trigger must be in the future. A repeating RTC alarm whose
     * first trigger is today's elapsed midnight can fire immediately when
     * the app starts, racing the explicit startup agenda refresh.
     */
    static long nextDailyUpdateMillis(DateTime now) {
        return now.plusDays(1).withTimeAtStartOfDay().getMillis();
    }

    public void setDailyUpdateAlarm(Context ctx) {
        // intent our receiver will receive
        Intent intent = new Intent(ctx, AlarmReceiver.class);
        intent.putExtra(IntentParams.EXTRA_ACTION, IntentParams.ACTION_DAILY_UPDATE);
        PendingIntent dailyAlarm = PendingIntent.getBroadcast(
                ctx,
                IntentParams.DAILY_UPDATE_ID,
                intent,
                PendingIntentFlags.immutable(PendingIntent.FLAG_CANCEL_CURRENT));
        AlarmManager alarmManager = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    nextDailyUpdateMillis(DateTime.now()),
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
                        if (!Agenda.instance().createRemindersForDailyUpdate(context)) {
                            // Propagate a failed nested transaction to the
                            // outer SQLiteOpenHelper transaction for rollback.
                            throw new SQLException("Could not create daily reminders");
                        }
                        return null;
                    }
                });
                // SQLite must commit and all alarms must be rescheduled before
                // the day can be recorded as complete. Partial scheduling
                // remains retryable after a service or process failure.
                // Clear obsolete reminder rows and Android registrations
                // only after the outer SQLite transaction has committed.
                Agenda.instance().cleanReminders(context);
                Agenda.instance().updateAllAlarms(context);
                // This runs on both startup and background workers; a durable
                // marker avoids reporting completion before it reaches disk.
                boolean persisted = PreferenceUtils.edit()
                        .putString(PreferenceKeys.AGENDA_LAST_UPDATED.key(), today.toString(localDateFmt))
                        .commit();
                if (!persisted) {
                    LogUtil.e(TAG, "Could not persist agenda completion marker");
                    return;
                }
                CalendulaApp.eventBus().post(new AgendaUpdatedEvent());
            } catch (SQLException | RuntimeException e) {
                // Do not publish success or suppress a later retry.
                LogUtil.e(TAG, "Error updating agenda; will retry on next update", e);
            }
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
        LogUtil.d(TAG, "Current reminder count: " + DB.eventReminders().count());
    }

    private void onBeforeUpdate(Context context, LocalDate today) {
        for (AgendaUpdateListener l : updateListeners) {
            l.onBeforeUpdate(context, today);
        }
    }

    private boolean needsToBeUpdated(LocalDate today) {
        return shouldRefreshAgenda(today,
                PreferenceUtils.getString(PreferenceKeys.AGENDA_LAST_UPDATED, null));
    }

    /**
     * Malformed stored preferences must not crash startup and permanently
     * suppress medication schedule reconstruction. Rebuild safely instead.
     */
    static boolean shouldRefreshAgenda(LocalDate today, String savedDate) {
        if (savedDate == null) {
            return true;
        }
        try {
            return !today.equals(ISODateTimeFormat.basicDate().parseLocalDate(savedDate));
        } catch (IllegalArgumentException invalidDate) {
            // Pure fallback: JVM tests and startup work without Android logging.
            // Do not expose the invalid preference (or user data) in logs.
            return true;
        }
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

    /**
     * Alarm identity must not depend on mutable patient names, time or other
     * hashCode fields. A unique URI keeps reminders distinct from each other
     * and from the daily-update PendingIntent sharing AlarmReceiver.
     */
    static Intent reminderBroadcastIntent(Context context, EventReminder reminder) {
        if (reminder == null || reminder.getId() == null || reminder.getId() <= 0) {
            throw new IllegalArgumentException("Save a reminder before scheduling its alarm");
        }
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.setAction(IntentParams.ACTION_ALARM_REMINDER);
        intent.setData(Uri.parse("content://" + context.getPackageName()
                + "/calendula-reminders/" + reminder.getId()));
        intent.putExtra(IntentParams.EXTRA_ACTION, IntentParams.ACTION_ALARM_REMINDER);
        intent.putExtra(IntentParams.EXTRA_REMINDER_ID, reminder.getId());
        return intent;
    }

    private PendingIntent getReminderIntent(Context context, EventReminder reminder) {
        return PendingIntent.getBroadcast(context, 0,
                reminderBroadcastIntent(context, reminder),
                PendingIntentFlags.immutable(PendingIntent.FLAG_UPDATE_CURRENT));
    }

    /** Best effort: cancel a legacy hash-based alarm with its former identity. */
    private void cancelLegacyAlarm(Context context, EventReminder reminder,
                                   AlarmManager alarmManager) {
        Intent legacy = new Intent(context, AlarmReceiver.class);
        legacy.putExtra(IntentParams.EXTRA_ACTION, IntentParams.ACTION_ALARM_REMINDER);
        legacy.putExtra(IntentParams.EXTRA_REMINDER_ID, reminder.getId());
        PendingIntent old = PendingIntent.getBroadcast(context, reminder.hashCode(),
                legacy, PendingIntentFlags.immutable(PendingIntent.FLAG_NO_CREATE));
        if (old != null) {
            alarmManager.cancel(old);
            old.cancel();
        }
    }

    // Package-private to verify actual PendingIntent cancellation on emulators.
    void cancelAlarm(Context context, EventReminder reminder) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            // FLAG_NO_CREATE must not create a new token merely to cancel it.
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0,
                    reminderBroadcastIntent(context, reminder),
                    PendingIntentFlags.immutable(PendingIntent.FLAG_NO_CREATE));
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent);
                pendingIntent.cancel();
            }
            cancelLegacyAlarm(context, reminder, alarmManager);
        }
    }

    private int repeatFreqSeconds() {
        return ReminderTiming.repeatSeconds();
    }

    public static class AgendaUpdatedEvent {

    }
}