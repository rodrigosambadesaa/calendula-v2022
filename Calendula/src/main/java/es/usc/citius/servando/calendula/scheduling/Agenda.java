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
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.persistence.ScheduleUtils;
import es.usc.citius.servando.calendula.util.stock.StockUpdater;
import es.usc.citius.servando.calendula.util.alerts.StockAlertHandler;
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
        // Snapshot new SQL rows so we can schedule only these after commit.
        // Re-arming every existing reminder could fire an old past-due alarm.
        final List<EventReminder> newlyPersisted = new ArrayList<>();
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
                                // Only persist SQL rows in the transaction.
                                // AlarmManager has no SQLite rollback.
                                DB.eventReminders().save(reminder);
                                if (allowPlatformEffects) {
                                    newlyPersisted.add(reminder);
                                }
                            } else {
                                LogUtil.d(TAG, "Event at can not be scheduled");
                            }
                        } else {
                            LogUtil.d(TAG, "Reminder already exist for " + e.getType() + " at " + e.getTime().toString());
                        }
                    }
                    // No platform alarms, notifications, or preference changes
                    // are permitted before the SQLite transaction commits.
                    return null;
                }
            });
            if (allowPlatformEffects) {
                // Retire obsolete alarms after SQL commit, then register only
                // newly inserted reminders that survived cleanup. Do not
                // re-arm old records whose nextTime may already be in the past.
                cleanReminders(context);
                for (EventReminder inserted : newlyPersisted) {
                    if (inserted.getId() == null) {
                        throw new IllegalStateException("Reminder persistence returned no ID");
                    }
                    EventReminder persisted = DB.eventReminders().findById(inserted.getId());
                    if (persisted != null) {
                        setAlarm(context, persisted);
                    }
                }
            }
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
        int rearmed = rearmPendingFutureReminders(context);
        LogUtil.d(TAG, "Rearmed " + rearmed + " eligible future reminders");
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
        // Legacy, malformed or incomplete PendingIntents may omit the stable
        // SQL row ID. Reject them before ORMLite attempts a null/invalid query.
        // Never act on a different reminder or invent a default identity.
        if (reminderId == null || reminderId <= 0L) {
            LogUtil.w(TAG, "Ignoring alarm without a persisted reminder ID");
            return;
        }
        EventReminder r = DB.eventReminders().findById(reminderId);
        if (r != null) {
            if (r.getNextTime() == null || r.getNextTime().isAfterNow()) {
                // An in-flight old PendingIntent may fire after the user has
                // postponed a dose. If its intake remains pending, never deliver
                // before the latest persisted time. Only re-arm the current
                // revision; another action may have rescheduled it already.
                if (DB.eventInstances().existsPending(
                        r.getEventType(), r.getDateTime(), r.getPatient())) {
                    if (r.getNextTime() != null && isCurrentPendingReminder(r)) {
                        setAlarm(ctx, r);
                    }
                    LogUtil.d(TAG, "Ignoring premature or superseded reminder broadcast");
                    return;
                }
                // No pending intake: preserve existing orphan/inactive reminder
                // cleanup, which verifies SQLite deletion before retiring OS
                // tokens. Do not leave cancelled/completed old alarms behind.
            }
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
            // INTERVAL_DAY is a fixed 24 hours, but calendar days around DST
            // can last 23 or 25 hours. Each daily broadcast registers the
            // next LOCAL midnight instead of drifting by an hour.
            // This inexact one-shot requires no special exact-alarm access.
            alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextDailyUpdateMillis(DateTime.now()),
                    dailyAlarm
            );
        }
    }

    public void start(final Context context) {
        setDailyUpdateAlarm(context);
        onDailyUpdate(context);
        // A same-day process restart may follow a crash between SQLite COMMIT
        // and OS registration. Re-arm valid future tokens from persisted SQL.
        // Do not fire old past-due reminders or resurrect cancelled intakes.
        rearmPendingFutureReminders(context);
    }

    /**
     * Reconcile reminders on ordinary app startup, not only at midnight or
     * ACTION_BOOT_COMPLETED. A stable PendingIntent identity ensures rearming
     * an already scheduled future reminder replaces rather than duplicates it.
     *
     * @return the number of valid future reminders submitted to AlarmManager.
     */
    int rearmPendingFutureReminders(Context context) {
        int count = 0;
        final DateTime now = DateTime.now();
        for (EventReminder reminder : DB.eventReminders().findAll()) {
            if (reminder == null || reminder.getId() == null
                    || reminder.getNextTime() == null
                    || !reminder.getNextTime().isAfter(now)) {
                continue;
            }
            if (!DB.eventInstances().existsPending(
                    reminder.getEventType(), reminder.getDateTime(),
                    reminder.getPatient())) {
                continue;
            }
            setAlarm(context, reminder);
            count++;
        }
        return count;
    }


    public void onDailyUpdate(final Context context) {
        final LocalDate today = LocalDate.now();
        if (needsToBeUpdated(today)) {
            // Snapshot rows before SQLite deletes them, then retire their OS
            // tokens strictly after the transaction commits.
            final List<EventReminder> expiredReminders = new ArrayList<>();
            try {
                TransactionManager.callInTransaction(DB.helper().getConnectionSource(), new Callable<Object>() {
                    @Override
                    public Object call() throws Exception {
                        // remove old reminders
                        DateTime cutoff = today.minusDays(1).toDateTimeAtStartOfDay();
                        expiredReminders.addAll(expiredRemindersBefore(
                                DB.eventReminders().findAll(), cutoff));
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
                // The database deletion has committed: retire obsolete OS
                // alarm tokens before reconciling surviving reminders.
                for (EventReminder removed : expiredReminders) {
                    if (removed.getId() != null && removed.getId() > 0) {
                        cancelAlarm(context, removed);
                        IntakeNotificationMgr.cancel(context, removed);
                    }
                }
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

    /**
     * Delete by persisted ID and verify the row is absent before retiring an OS
     * alarm. GenericDao.remove() discards the affected-row count and a silent
     * zero-row SQLite delete must not cancel an alarm for a surviving row.
     */
    private void deletePersistedReminderForAlarmCleanup(EventReminder reminder) {
        if (reminder == null || reminder.getId() == null) {
            throw new IllegalArgumentException("Persisted reminder ID required");
        }
        final int deleted;
        try {
            deleted = DB.eventReminders().delete(reminder);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not delete persisted reminder", e);
        }
        if (deleted > 1 || DB.eventReminders().findById(reminder.getId()) != null) {
            throw new IllegalStateException("Reminder still persisted after SQLite delete");
        }
    }

    public void cleanReminderIfPossible(Context context, Patient patient, EventType type, DateTime time) {
        // A cancelled dose is not pending, even when completed is false.
        if (!DB.eventInstances().existsPending(type, time, patient)) {
            EventReminder r = DB.eventReminders().findBy(type, time, patient);
            if (r == null) {
                // Another cleanup may already have removed this reminder.
                return;
            }
            // Verify committed SQLite deletion before revoking platform tokens.
            deletePersistedReminderForAlarmCleanup(r);
            // Once removal succeeds, retire stable and matching legacy tokens.
            cancelAlarm(context, r);
            IntakeNotificationMgr.cancel(context, r);
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
            // Keep Android alarm if SQLite silently left a persisted row.
            deletePersistedReminderForAlarmCleanup(r);
            cancelAlarm(context, r);
            IntakeNotificationMgr.cancel(context, r);
        }
        logReminders();
    }

    public void onDelayReminder(Context context, EventType type, DateTime time, Patient p, int delay) {
        EventReminder reminder = DB.eventReminders().findBy(type, time, p);
        delayReminder(context, reminder, delay);
    }


    /**
     * Direct reminder confirmation and stock deductions belong to the same
     * transaction as reminder deletion. Never mark a dose taken without also
     * updating its managed inventory. The notification worker normally uses
     * ScheduleUtils.checkIntakeEvents; this public alternative must be safe too.
     */
    private void finalizeReminderInSqlite(Context context, EventReminder reminder,
                                          boolean confirmIntake) {
        if (reminder == null || reminder.getId() == null) {
            throw new IllegalArgumentException("A persisted reminder ID is required");
        }
        final List<Long> stockScheduleIds = new ArrayList<>();
        final boolean[] eventsChanged = {false};
        try {
            TransactionManager.callInTransaction(DB.helper().getConnectionSource(),
                    (Callable<Void>) () -> {
                        // Reject a stale callback that raced with another
                        // reminder action; do not update any unrelated intake.
                        if (DB.eventReminders().findById(reminder.getId()) == null) {
                            throw new SQLException("Reminder removed before decision");
                        }
                        if (confirmIntake) {
                            Patient patient = reminder.getPatient();
                            if (reminder.getEventType() != EventType.MEDICATION_INTAKE
                                    || patient == null || patient.getId() == null) {
                                throw new SQLException("Cannot confirm an unassigned medication reminder");
                            }
                            List<EventInstance> pending = DB.eventInstances().findPending(
                                    reminder.getEventType(), reminder.getDateTime(), patient);
                            if (pending.isEmpty()) {
                                throw new SQLException("No pending medication doses remain");
                            }
                            final DateTime completedAt = DateTime.now();
                            for (EventInstance selected : pending) {
                                if (selected == null || selected.getId() == null) {
                                    throw new SQLException("Pending intake identity is missing");
                                }
                                EventInstance stored = DB.eventInstances().findById(selected.getId());
                                if (stored == null || stored.getType() != EventType.MEDICATION_INTAKE
                                        || stored.completed() || stored.cancelled()
                                        || stored.getPatient() == null
                                        || !patient.getId().equals(stored.getPatient().getId())
                                        || !reminder.getDateTime().equals(stored.getTime())) {
                                    throw new SQLException("Intake changed before reminder confirmation");
                                }
                                stored.setCompleted(true);
                                stored.setCompletedAt(completedAt);
                                final boolean stockChanged =
                                        StockUpdater.applyStockForTransition(stored, false);
                                if (DB.eventInstances().update(stored) != 1) {
                                    throw new SQLException("Expected one completed intake row");
                                }
                                if (stockChanged) {
                                    stockScheduleIds.add(stored.getRef());
                                }
                                eventsChanged[0] = true;
                            }
                        } else {
                            eventsChanged[0] = DB.eventInstances().cancelUncompleted(
                                    reminder.getEventType(), reminder.getDateTime(),
                                    reminder.getPatient(), DateTime.now()) > 0;
                        }
                        final int deleted = DB.eventReminders().delete(reminder);
                        if (deleted > 1 || DB.eventReminders().findById(reminder.getId()) != null) {
                            throw new SQLException("Reminder persisted after attempted deletion");
                        }
                        return null;
                    });
        } catch (SQLException e) {
            throw new IllegalStateException("Could not atomically finalize medication reminder", e);
        }
        // Platform scheduling, stock alerts and UI events are not part of
        // SQLite. Trigger them only after all event, stock and reminder rows
        // have committed successfully.
        cancelAlarm(context, reminder);
        if (!stockScheduleIds.isEmpty()) {
            try {
                for (Long id : stockScheduleIds) {
                    Schedule schedule = DB.schedules().findById(id);
                    if (schedule != null && schedule.getMedicine() != null) {
                        StockAlertHandler.checkStockAlerts(schedule.getMedicine());
                    }
                }
                DB.medicines().fireEvent();
            } catch (RuntimeException error) {
                LogUtil.e(TAG, "Committed stock alert could not be refreshed", error);
            }
        }
        if (eventsChanged[0]) {
            try {
                DB.eventInstances().fireEvent();
            } catch (RuntimeException error) {
                LogUtil.e(TAG, "Committed intake UI event could not be published", error);
            }
        }
    }

    public void confirmReminder(Context context, Long reminderId) {
        EventReminder reminder = DB.eventReminders().findById(reminderId);
        if (reminder != null) {
            finalizeReminderInSqlite(context, reminder, true);
            IntakeNotificationMgr.cancel(context, reminder);
        }
    }

    /** A missing/already handled reminder must not produce a success toast. */
    public boolean cancelReminder(Context context, Long reminderId) {
        if (reminderId == null || reminderId <= 0L) {
            return false;
        }
        EventReminder reminder = DB.eventReminders().findById(reminderId);
        if (!isCurrentPendingReminder(reminder)) {
            // A stale action must neither tell the user a medicine was
            // cancelled nor retire an independently completed/already removed
            // reminder. Alarm recovery excludes inactive rows separately.
            return false;
        }
        removeReminder(context, reminder);
        IntakeNotificationMgr.cancel(context, reminder);
        return true;
    }

    public void removeReminder(Context context, EventReminder reminder) {
        // Also called for expired notifications; retain their notification
        // behavior by cancelling OS alarms here, not UI notifications.
        finalizeReminderInSqlite(context, reminder, false);
    }

    /** Returns false for a stale or invalid notification action. */
    public boolean delayReminder(Context context, Long reminderId) {
        if (reminderId == null || reminderId <= 0L) {
            return false;
        }
        return delayReminder(context, DB.eventReminders().findById(reminderId), null);
    }

    /**
     * Persist a reminder delay before modifying its Android notification/alarm.
     * A rejected SQLite write must retain the old persisted time, caller model,
     * and existing OS token. Re-read by ID so a stale callback cannot recreate
     * a deleted reminder or overwrite a newer delay.
     */
    public boolean delayReminder(Context context, EventReminder reminder, Integer delay) {
        if (reminder == null) {
            return false;
        }
        if (reminder.getId() == null || reminder.getId() <= 0) {
            throw new IllegalArgumentException("A persisted reminder ID is required");
        }
        int seconds = delay != null ? delay : repeatFreqSeconds();
        if (seconds <= 0) {
            throw new IllegalArgumentException("Reminder delay must be positive");
        }
        final DateTime delayedUntil = DateTime.now().plusSeconds(seconds);
        final EventReminder committed;
        try {
            committed = TransactionManager.callInTransaction(
                    DB.helper().getConnectionSource(), (Callable<EventReminder>) () -> {
                        EventReminder current = DB.eventReminders().findById(reminder.getId());
                        if (current == null) {
                            throw new SQLException("Reminder was removed before delaying");
                        }
                        if (reminder.getNextTime() != null
                                && !reminder.getNextTime().equals(current.getNextTime())) {
                            throw new SQLException("Reminder was already rescheduled");
                        }
                        if (!DB.eventInstances().existsPending(
                                current.getEventType(), current.getDateTime(),
                                current.getPatient())) {
                            // A completed/cancelled dose makes this stale button
                            // a no-op, not a successful medication delay.
                            return null;
                        }
                        current.setNextTime(delayedUntil);
                        if (DB.eventReminders().update(current) != 1) {
                            throw new SQLException("Expected exactly one delayed reminder row");
                        }
                        return current;
                    });
        } catch (SQLException e) {
            throw new IllegalStateException("Could not persist medication reminder delay", e);
        }
        if (committed == null) {
            return false;
        }
        // Only committed SQLite state may be reflected into the caller model
        // or the non-transactional Android AlarmManager and notification APIs.
        reminder.setNextTime(committed.getNextTime());
        if (isCurrentPendingReminder(committed)) {
            setAlarm(context, committed);
            IntakeNotificationMgr.cancel(context, committed);
            return true;
        }
        // Another action may have completed/cancelled the intake after commit.
        // Never tell the user that an inactive reminder was rescheduled.
        return false;
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

    /** Read-only identity snapshot of rows pruned by the daily SQL cutoff. */
    static List<EventReminder> expiredRemindersBefore(
            Collection<EventReminder> reminders, DateTime cutoff) {
        if (reminders == null || cutoff == null) {
            throw new IllegalArgumentException("Reminder snapshot and cutoff are required");
        }
        List<EventReminder> expired = new ArrayList<>();
        for (EventReminder reminder : reminders) {
            if (reminder == null || reminder.getDateTime() == null) {
                throw new IllegalArgumentException("Malformed persisted reminder");
            }
            if (reminder.getDateTime().isBefore(cutoff)) {
                expired.add(reminder);
            }
        }
        return expired;
    }

    private void removeRemindersBefore(LocalDate date) {
        DB.eventReminders().removeOlderThan(date.toDateTimeAtStartOfDay());
    }

    /**
     * A receiver may finalize a stale/lost dose and remove the reminder while
     * this alarm callback is still running. Never recreate that row using an
     * old in-memory EventReminder, nor overwrite an independently delayed dose.
     */
    boolean isCurrentPendingReminder(EventReminder reminder) {
        if (reminder == null || reminder.getId() == null || reminder.getNextTime() == null) {
            return false;
        }
        EventReminder persisted = DB.eventReminders().findById(reminder.getId());
        return persisted != null
                && reminder.getNextTime().equals(persisted.getNextTime())
                && DB.eventInstances().existsPending(
                        persisted.getEventType(), persisted.getDateTime(),
                        persisted.getPatient());
    }

    private void sendReminderToReceiver(Context ctx, EventReminder r) {
        // Do not deliver a reminder for another patient or inactive intake.
        boolean eventExist = DB.eventInstances().existsPending(
                r.getEventType(), r.getDateTime(), r.getPatient());
        LogUtil.d(TAG, "There are events to remind!");
        if (eventExist) {
            // Persisted legacy reminder types may outlive the module that
            // registered their delivery handler. An absent receiver must not
            // crash AlarmIntentService or mutate unrelated medication data.
            // Keep the SQL row for inspection/recovery; never claim delivery.
            EventReminderReceiver receiver = receivers.get(r.getEventType());
            if (receiver == null) {
                LogUtil.w(TAG, "Ignoring reminder with no registered event receiver");
                return;
            }
            LogUtil.d(TAG, "Sending event to " + r.getEventType() + " receiver");
            receiver.onEvent(ctx, r);
            // auto repeat
            if (r.autoRepeat()) {
                LogUtil.d(TAG, "Auto repeat enabled, try to reschedule repeat");
                DateTime nextTime = DateTime.now().plusSeconds(repeatFreqSeconds());
                if (!rescheduleAutoRepeatIfCurrent(ctx, r, nextTime)) {
                    LogUtil.d(TAG, "Reminder no longer pending or outside repeat window");
                }
            }
        } else {
            // remove the reminder
            LogUtil.d(TAG, "Cancelling reminder with id " + r.getId());
            // Keep deletion ordering consistent across all alarm cleanup paths.
            deletePersistedReminderForAlarmCleanup(r);
            cancelAlarm(ctx, r);
        }
    }

    /**
     * Only the reminder revision that actually fired may advance auto-repeat.
     * Another action can cancel the dose or postpone it between notification
     * delivery and repeat scheduling; both predicates and the SQL update must
     * therefore share one transaction. AlarmManager is updated post-commit.
     *
     * Package-private for real SQLite/alarm regression tests.
     */
    boolean rescheduleAutoRepeatIfCurrent(Context context, EventReminder snapshot,
                                          DateTime nextTime) {
        if (snapshot == null || snapshot.getId() == null || snapshot.getId() <= 0
                || snapshot.getDateTime() == null || snapshot.getNextTime() == null
                || nextTime == null || !nextTime.isAfter(snapshot.getNextTime())
                || !shouldReschedule(snapshot, nextTime)) {
            return false;
        }
        final EventReminder committed;
        try {
            committed = TransactionManager.callInTransaction(
                    DB.helper().getConnectionSource(), (Callable<EventReminder>) () -> {
                        EventReminder current = DB.eventReminders().findById(snapshot.getId());
                        if (current == null || !current.autoRepeat()
                                || current.getDateTime() == null || current.getNextTime() == null
                                || !snapshot.getNextTime().equals(current.getNextTime())
                                || !nextTime.isAfter(current.getNextTime())
                                || !shouldReschedule(current, nextTime)
                                || current.getEventType() != snapshot.getEventType()
                                || !current.getDateTime().equals(snapshot.getDateTime())
                                || !DB.eventInstances().existsPending(
                                        current.getEventType(), current.getDateTime(),
                                        current.getPatient())) {
                            return null;
                        }
                        current.setNextTime(nextTime);
                        if (DB.eventReminders().update(current) != 1) {
                            throw new SQLException("Expected one auto-repeat reminder row");
                        }
                        return current;
                    });
        } catch (SQLException failed) {
            throw new IllegalStateException(
                    "Could not persist automatic reminder repetition", failed);
        }
        if (committed == null) {
            return false;
        }
        snapshot.setNextTime(committed.getNextTime());
        // A concurrent action after SQL COMMIT may have retired this reminder.
        // Never resurrect a stale dose from a cached EventReminder snapshot.
        if (!isCurrentPendingReminder(committed)) {
            return false;
        }
        setAlarm(context, committed);
        return true;
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