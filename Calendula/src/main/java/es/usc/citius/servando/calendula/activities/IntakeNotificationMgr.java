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

package es.usc.citius.servando.calendula.activities;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.util.Pair;
import android.text.SpannableStringBuilder;

import org.joda.time.DateTime;
import org.joda.time.LocalTime;
import org.joda.time.format.ISODateTimeFormat;

import java.util.List;
import java.util.Random;

import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.login.LoginStateManager;
import es.usc.citius.servando.calendula.notifications.LockScreenAlarmActivity;
import es.usc.citius.servando.calendula.notifications.NotificationHelper;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.scheduling.AlarmIntentService;
import es.usc.citius.servando.calendula.scheduling.AlarmReceiver;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;
import es.usc.citius.servando.calendula.util.AvatarMgr;
import es.usc.citius.servando.calendula.util.IntentParams;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

/**
 * Helper class for showing and canceling intake notifications
 * notifications.
 */
public class IntakeNotificationMgr {

    private static final String TAG = "ReminderNotification";

    private static Random random = new Random();

    public static void notify(final Context context, EventReminder reminder, boolean lost) {

        if (!PreferenceUtils.getBoolean(PreferenceKeys.SETTINGS_ALARM_NOTIFICATIONS, true)) {
            return;
        }

        // Don't notify if user isn't logged in (for example when resetting PIN
        if (!LoginStateManager.getInstance().isLoggedIn()) {
            return;
        }

        String title = context.getResources().getString(R.string.meds_time);

        EventType type = reminder.getEventType();
        DateTime dateTime = reminder.getDateTime();
        Patient patient = reminder.getPatient();
        LocalTime time = dateTime.toLocalTime();

        List<EventInstance> events = DB.eventInstances().find(type, dateTime, patient);

        NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
        style.setBigContentTitle(title);

        for (EventInstance e : events) {
            Schedule schedule = DB.schedules().findById(e.getRef());
            Medicine medicine = schedule.getMedicine();
            Double dose = e.getDoubleParam(EventInstance.PARAM_DOSE);
            SpannableStringBuilder ssb = new SpannableStringBuilder();

            if (schedule.hasState(Schedule.ScheduleState.CREATED_FROM_OFFICIAL)) {
                ssb.append(medicine.getName());
                if (schedule.hasState(Schedule.ScheduleState.DIFFERS_FROM_OFFICIAL)) {
                    ssb.append(context.getString(R.string.notification_msg_official_schedule_modified));
                } else {
                    ssb.append(context.getString(R.string.notification_msg_official_schedule));
                }
            } else {
                ssb.append(medicine.getName());
                ssb.append(":  " + dose + " " + medicine.getPresentation().units(context.getResources(), dose) + " ");
            }
            style.addLine(ssb);
        }

        String delayMinutesStr = PreferenceUtils.getString(PreferenceKeys.SETTINGS_ALARM_REPEAT_FREQUENCY, "15");
        int delayMinutes = (int) Long.parseLong(delayMinutesStr);

        if (delayMinutes > 0 && !lost) {
            String repeatTime = DateTime.now().plusMinutes(delayMinutes).toString("HH:mm");
            style.setSummaryText(context.getResources().getString(R.string.notification_repeat_message, repeatTime));
        } else {
            style.setSummaryText(events.size() + " " + context.getString(R.string.medicine) + (events.size() > 1 ? "s, " : ", "));
        }

        Pair<Intent, Intent> intents = null;

        if (!lost) {
            // delay intent sent on click delay button
            final Intent delay = new Intent(context, AlarmReceiver.class);
            delay.putExtra(IntentParams.EXTRA_ACTION, IntentParams.ACTION_ALARM_DELAY);
            delay.putExtra(IntentParams.EXTRA_REMINDER_ID, reminder.getId());
            // delay intent sent on click delay button
            final Intent cancel = new Intent(context, AlarmReceiver.class);
            cancel.putExtra(IntentParams.EXTRA_ACTION, IntentParams.ACTION_ALARM_CANCEL);
            cancel.putExtra(IntentParams.EXTRA_REMINDER_ID, reminder.getId());
            intents = new Pair<>(delay, cancel);
        }

        final Intent confirmAll = new Intent(context, AlarmReceiver.class);
        confirmAll.putExtra(IntentParams.EXTRA_ACTION, IntentParams.ACTION_ALARM_CONFIRM);
        confirmAll.putExtra(IntentParams.EXTRA_REMINDER_ID, reminder.getId());

        NotificationOptions options = new NotificationOptions();
        options.style = style;
        options.lost = lost;
        options.when = dateTime.getMillis();
        options.tag = makeTag(reminder);
        options.notificationNumber = events.size();
        options.picture = getLargeIcon(context.getResources(), reminder.getPatient());
        options.text = events.size() + " " + context.getString(R.string.home_menu_medicines).toLowerCase();

        final Intent defaultIntent = new Intent(context, ConfirmActivity.class);
        defaultIntent.putExtra(IntentParams.EXTRA_ACTION, IntentParams.ACTION_ALARM_REMINDER);
        defaultIntent.putExtra(IntentParams.EXTRA_PATIENT_ID, patient.getId());
        defaultIntent.putExtra(IntentParams.EXTRA_DATETIME, dateTime.toString(ISODateTimeFormat.dateTimeNoMillis()));

        notify(context, reminder.getId().intValue(), title, intents, confirmAll, defaultIntent, options);
        showInsistentScreen(context, defaultIntent);
    }

    /**
     * Cancels any notifications of this type previously shown using
     */
    @TargetApi(Build.VERSION_CODES.ECLAIR)
    public static void cancel(final Context context, EventReminder e) {
        if (e != null) {
            final NotificationManagerCompat nm = NotificationManagerCompat.from(context);
            nm.cancel(makeTag(e), e.getId().intValue());
        }
    }

    private static String makeTag(EventReminder reminder) {
        return "REMINDER-" + reminder.getId();
    }

    private static void showInsistentScreen(Context context, Intent i) {
        boolean insistentNotifications = PreferenceUtils.getBoolean(PreferenceKeys.SETTINGS_ALARM_INSISTENT, false);
        if (insistentNotifications) {
            Intent intent = new Intent(context, LockScreenAlarmActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra("target", i);
            context.startActivity(intent);
        }
    }

    private static void notify(final Context context, int id, final String title,
                               Pair<Intent, Intent> actionIntents, Intent confirmIntent, Intent intent,
                               NotificationOptions options) {

        boolean notifications = PreferenceUtils.getBoolean(PreferenceKeys.SETTINGS_ALARM_NOTIFICATIONS, true);


        // if notifications are disabled, exit
        if (!notifications) {
            return;
        }

        final Resources res = context.getResources();
        // prepare notification intents
        PendingIntent defaultIntent = PendingIntent.getActivity(context, random.nextInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent delayIntent = null;
        PendingIntent cancelIntent = null;
        PendingIntent confirmAllIntent = null;

        if (!options.lost) {
            delayIntent = PendingIntent.getBroadcast(context, random.nextInt(), actionIntents.first, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            cancelIntent = PendingIntent.getBroadcast(context, random.nextInt(), actionIntents.second, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            confirmAllIntent = PendingIntent.getBroadcast(context, random.nextInt(), confirmIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }

        int ic = options.lost ? R.drawable.ic_pill_small_lost : R.drawable.ic_pill_small;
        options.picture = options.picture != null ? options.picture : BitmapFactory.decodeResource(res, ic);
        options.title = title;
        options.ticker = title;
        options.defaultIntent = defaultIntent;
        if (!options.lost) {
            options.cancelIntent = cancelIntent;
            options.delayIntent = delayIntent;
            options.confirmAllIntent = confirmAllIntent;
        }
        options.ringtone = getRingtoneUri();

        Notification n = buildNotification(context, options);
        notify(context, id, n, options.tag);
    }


    private static Notification buildNotification(Context context, NotificationOptions options) {

        Resources res = context.getResources();
        boolean insistentNotifications = PreferenceUtils.getBoolean(PreferenceKeys.SETTINGS_ALARM_INSISTENT, false);

        final NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NotificationHelper.CHANNEL_MEDS_ID)

                // Set appropriate defaults for the notification light, sound, and vibration.
                .setDefaults(Notification.DEFAULT_ALL)
                // Set required fields, including the small icon, the notification title, and text.
                .setSmallIcon(options.lost ? R.drawable.ic_pill_small_lost : R.drawable.ic_pill_small)
                .setContentTitle(options.title)
                .setContentText(options.text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                // Set ticker text (preview) information for this notification.
                .setTicker(options.ticker)
                // Show a number. This is useful when stacking notifications of a single type.
                .setNumber(options.notificationNumber)
                .setWhen(options.when)
                // Set the pending intent to be initiated when the user touches the notification.
                .setContentIntent(options.defaultIntent)
                // Show an expanded list of items on devices running Android 4.1
                // or later.
                .setStyle(options.style)
                // Automatically dismiss the notification when it is touched.
                .setAutoCancel(true);

        if (!insistentNotifications) {
            // if insistent is enabled, an activity with vibration will start
            builder.setVibrate(new long[]{1000, 200, 100, 500, 400, 200, 100, 500, 400, 200, 100, 500, 1000}).setSound(options.ringtone);
        }

        if (!options.lost) {
            // add delay button and cancel button
            builder.addAction(0, res.getString(R.string.notification_delay), options.delayIntent)
                    .addAction(0, res.getString(R.string.notification_cancel_now), options.cancelIntent)
                    .addAction(0, res.getString(R.string.notification_taken), options.confirmAllIntent);

            builder.extend((new NotificationCompat.WearableExtender()
                    .addAction(new NotificationCompat.Action.Builder(
                            R.drawable.ic_done_white_36dp,
                            res.getString(R.string.done),
                            options.confirmAllIntent).build())
                    .addAction(new NotificationCompat.Action.Builder(
                            R.drawable.ic_history_white_24dp,
                            res.getString(R.string.notification_delay),
                            options.delayIntent).build())
                    .addAction(new NotificationCompat.Action.Builder(
                            R.drawable.ic_alarm_off_white_24dp,
                            res.getString(R.string.notification_cancel_now),
                            options.cancelIntent).build()
                    )));
        }

        Notification n = builder.build();
        n.defaults = 0;
        n.ledARGB = 0x00ffa500;
        n.ledOnMS = 1000;
        n.ledOffMS = 2000;
        return n;
    }


    private static Uri getRingtoneUri() {
        String r = PreferenceUtils.getString(PreferenceKeys.SETTINGS_NOTIFICATION_TONE, null);
        Uri sound = r != null ? Uri.parse(r) : RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        return sound != null ? sound : Settings.System.DEFAULT_NOTIFICATION_URI;
    }

    private static Bitmap getLargeIcon(Resources r, Patient p) {
        return BitmapFactory.decodeResource(r, AvatarMgr.res(p.getAvatar()));
    }


    @TargetApi(Build.VERSION_CODES.ECLAIR)
    private static void notify(final Context context, int id, final Notification notification, String tag) {
        final NotificationManagerCompat nm = NotificationManagerCompat.from(context);
        nm.notify(tag, id, notification);
    }

    private static class NotificationOptions {
        int notificationNumber;
        boolean lost = false;
        String title;
        String text;
        String ticker;
        String tag;
        Bitmap picture;
        PendingIntent defaultIntent;
        PendingIntent cancelIntent;
        PendingIntent delayIntent;
        PendingIntent confirmAllIntent;
        NotificationCompat.InboxStyle style;
        Uri ringtone;
        long when;

    }

}