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

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.RingtoneManager;
import android.net.Uri;
import android.provider.Settings;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.util.Pair;
import android.text.SpannableStringBuilder;

import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Locale;

import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.fragments.DailyAgendaOfficialTreatmentValidator;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedEntity;
import es.usc.citius.servando.calendula.login.LoginStateManager;
import es.usc.citius.servando.calendula.notifications.LockScreenAlarmActivity;
import es.usc.citius.servando.calendula.notifications.NotificationHelper;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.scheduling.AlarmIntentService;
import es.usc.citius.servando.calendula.scheduling.AlarmReceiver;
import es.usc.citius.servando.calendula.scheduling.IntakeSnapshot;
import es.usc.citius.servando.calendula.scheduling.ReminderTiming;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;
import es.usc.citius.servando.calendula.util.AvatarMgr;
import es.usc.citius.servando.calendula.util.IntentParams;
import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.PendingIntentFlags;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

/**
 * Helper class for showing and canceling intake notifications
 * notifications.
 */
public class IntakeNotificationMgr {

    private static final String TAG = "IntakeNotificationMgr";
    private static final String PENDING_ACTION_PREFIX =
            "es.usc.citius.servando.calendula.notification.";

    /**
     * PendingIntent identity excludes extras. Use distinct stable Android
     * intent actions per notification button and use the reminder's persisted
     * ID as the request code, so updating an existing notification refreshes
     * the snapshot instead of leaking fresh random PendingIntents.
     */
    static Intent identifyNotificationAction(Intent intent, String buttonKind, long reminderId) {
        if (intent == null || reminderId <= 0L
                || !("OPEN".equals(buttonKind) || "DELAY".equals(buttonKind)
                || "CANCEL".equals(buttonKind) || "CONFIRM".equals(buttonKind)
                || "REVIEW".equals(buttonKind))) {
            throw new IllegalArgumentException("A valid reminder action identity is required");
        }
        intent.setAction(PENDING_ACTION_PREFIX + buttonKind);
        intent.setData(Uri.parse("calendula-reminder://notification/"
                + reminderId + "/" + buttonKind));
        return intent;
    }

    /**
     * Official prescription notification lines contain no numeric dose.
     * Never parse a missing/malformed legacy Bundle field in that branch.
     * Manually configured doses must have passed canRenderMedication first.
     */
    static Double doseForNotification(EventInstance event, Schedule schedule) {
        if (schedule.hasState(Schedule.ScheduleState.CREATED_FROM_OFFICIAL)) {
            return null;
        }
        return event.getDoubleParam(EventInstance.PARAM_DOSE);
    }

    /**
     * The medication name, schedule and unit/dose must be verifiable before
     * presenting any prescription-specific text or confirmation action.
     * An invalid record must produce a generic review alert instead.
     */
    static boolean canRenderMedication(EventInstance event, Schedule schedule,
                                       Patient expectedPatient) {
        if (event == null || event.getType() != EventType.MEDICATION_INTAKE
                || event.completed() || event.cancelled()
                || schedule == null || event.getRef() == null
                || expectedPatient == null || expectedPatient.getId() == null
                || event.getPatient() == null || event.getPatient().getId() == null
                || schedule.getPatient() == null || schedule.getPatient().getId() == null
                || !expectedPatient.getId().equals(event.getPatient().getId())
                || !expectedPatient.getId().equals(schedule.getPatient().getId())) {
            // A schedule for another patient must never leak medicine details.
            return false;
        }
        Medicine medicine = schedule.getMedicine();
        if (medicine == null || medicine.getName() == null
                || medicine.getName().trim().isEmpty()) {
            return false;
        }
        // A corrupt/imported schedule may refer to another patient's medicine
        // even when the event and schedule themselves have matching owners.
        // Preserve legacy shared catalog medicines with no patient owner, but
        // never display a specifically assigned medicine to a different user.
        Patient medicineOwner = medicine.getPatient();
        if (medicineOwner != null && (medicineOwner.getId() == null
                || !expectedPatient.getId().equals(medicineOwner.getId()))) {
            return false;
        }
        if (!schedule.hasState(Schedule.ScheduleState.CREATED_FROM_OFFICIAL)) {
            if (medicine.getPresentation() == null || event.getParams() == null) {
                return false;
            }
            try {
                String rawDose = event.getParams().getString(EventInstance.PARAM_DOSE);
                if (rawDose == null) {
                    return false;
                }
                double dose = Double.parseDouble(rawDose);
                if (Double.isNaN(dose) || Double.isInfinite(dose) || dose <= 0d) {
                    return false;
                }
            } catch (RuntimeException malformedDose) {
                // Legacy/corrupted Bundles may contain a non-String entry.
                // Never crash or display a guessed dosage.
                return false;
            }
        }
        return true;
    }

    /**
     * The exact text placed in the notification's expanded medication list.
     * Rendering is refused for inactive, unassigned or wrong-patient doses.
     * For imported official schedules, the caller must additionally verify
     * the live external prescription with canRenderOfficialSource().
     *
     * Package-visible so instrumented tests can assert actual displayed lines
     * without posting notifications or touching a signed-in user's session.
     */
    static CharSequence verifiedNotificationLine(Context context, EventInstance event,
                                                 Schedule schedule, Patient patient) {
        if (!canRenderMedication(event, schedule, patient)) {
            return null;
        }
        Medicine medicine = schedule.getMedicine();
        SpannableStringBuilder line = new SpannableStringBuilder();
        if (schedule.hasState(Schedule.ScheduleState.CREATED_FROM_OFFICIAL)) {
            line.append(medicine.getName());
            if (schedule.hasState(Schedule.ScheduleState.DIFFERS_FROM_OFFICIAL)) {
                line.append(context.getString(R.string.notification_msg_official_schedule_modified));
            } else {
                line.append(context.getString(R.string.notification_msg_official_schedule));
            }
        } else {
            Double dose = doseForNotification(event, schedule);
            line.append(medicine.getName());
            line.append(":  " + dose + " "
                    + medicine.getPresentation().units(context.getResources(), dose) + " ");
        }
        return line;
    }

    /**
     * A provider-bound schedule must reference the correct patient's live
     * treatment before a notification claims the prescription is official.
     * Legacy manually configured schedules remain displayable without one.
     */
    static boolean canRenderOfficialSource(Schedule schedule, ActiveMedEntity source) {
        if (schedule == null) {
            return false;
        }
        if (!schedule.isBoundToActiveMed()) {
            return !schedule.hasState(Schedule.ScheduleState.CREATED_FROM_OFFICIAL);
        }
        return DailyAgendaOfficialTreatmentValidator.verifiedLink(schedule, source);
    }

    /** No medicine names, doses, or 'taken/cancel' actions without valid data. */
    static Notification buildUnverifiedMedicationNotification(
            Context context, EventReminder reminder) {
        final Intent openApp = new Intent(context, StartActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        final PendingIntent openPendingIntent = PendingIntent.getActivity(
                context, reminder.getId().intValue(),
                identifyNotificationAction(openApp, "REVIEW", reminder.getId()),
                PendingIntentFlags.immutable(PendingIntent.FLAG_UPDATE_CURRENT));
        return new NotificationCompat.Builder(context, NotificationHelper.CHANNEL_MEDS_ID)
                .setSmallIcon(R.drawable.ic_pill_small_lost)
                .setContentTitle(context.getString(R.string.medication_unverified_title))
                .setContentText(context.getString(R.string.medication_unverified_description))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setWhen(reminder.getDateTime() == null
                        ? System.currentTimeMillis() : reminder.getDateTime().getMillis())
                .setContentIntent(openPendingIntent)
                .setAutoCancel(true)
                .build();
    }

    private static void alertUnverifiedMedicationData(Context context, EventReminder reminder) {
        // Do not include identifiers, medicine names or dosage details in logs.
        LogUtil.e(TAG, "Medication reminder has incomplete patient or schedule data");
        if (reminder == null || reminder.getId() == null
                || !NotificationHelper.canPostNotifications(context)) {
            return;
        }
        NotificationManagerCompat.from(context).notify(
                makeTag(reminder), reminder.getId().intValue(),
                buildUnverifiedMedicationNotification(context, reminder));
    }

    /**
     * A legacy or corrupt reminder must never become a notification with
     * actionable "Taken", "Cancel" or "Delay" controls. In particular, an
     * unrelated STOCK/PHARMACY event is not a medication intake, and a
     * nonpersisted patient must not query another patient's clinical rows.
     */
    static boolean isVerifiedMedicationReminderIdentity(EventReminder reminder) {
        return reminder != null && reminder.getId() != null
                && reminder.getId() > 0L
                && reminder.getEventType() == EventType.MEDICATION_INTAKE
                && reminder.getDateTime() != null
                && reminder.getPatient() != null
                && reminder.getPatient().getId() != null
                && reminder.getPatient().getId() > 0L;
    }

    public static void notify(final Context context, EventReminder reminder, boolean lost) {

        if (!NotificationFlagPolicy.configuredOrDefault(PreferenceKeys.SETTINGS_ALARM_NOTIFICATIONS, true)) {
            return;
        }

        // Don't notify if user isn't logged in (for example when resetting PIN
        if (!LoginStateManager.getInstance().isLoggedIn()) {
            return;
        }
        if (reminder == null) {
            LogUtil.e(TAG, "Null medication reminder received");
            return;
        }

        String title = context.getResources().getString(R.string.meds_time);

        EventType type = reminder.getEventType();
        DateTime dateTime = reminder.getDateTime();
        Patient patient = reminder.getPatient();
        // An unassigned patient cannot be used in ConfirmActivity; do not
        // make it possible to acknowledge an intake from invalid identity.
        if (!isVerifiedMedicationReminderIdentity(reminder)) {
            // Show only the existing generic review notice; never perform a
            // null/foreign type SQLite lookup or create dose-action buttons.
            alertUnverifiedMedicationData(context, reminder);
            return;
        }

        // Keep the displayed doses consistent with the patient-scoped,
        // uncompleted AND uncancelled event check in Agenda.
        List<EventInstance> events = DB.eventInstances().findPending(type, dateTime, patient);
        if (events.isEmpty()) {
            // An intake may have been confirmed/cancelled since alarm dispatch.
            // Do not display an empty or clinically stale notification.
            return;
        }

        NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
        style.setBigContentTitle(title);

        // Validate and render from the same schedule snapshot; hash exactly
        // these same verified objects when constructing the consent action.
        // No notification is posted until ALL pending doses pass validation.
        final Map<Long, Schedule> displayedSchedules = new HashMap<>();
        for (EventInstance event : events) {
            Schedule schedule = event == null || event.getRef() == null
                    ? null : DB.schedules().findById(event.getRef());
            // Reload the authoritative medicine row before checking its
            // ownership. ORMLite can hold an earlier medicine owner after an
            // independent SQLite reassignment.
            try {
                Medicine linked = schedule == null ? null : schedule.getMedicine();
                if (linked != null && (linked.getId() == null
                        || DB.medicines().refresh(linked) != 1)) {
                    alertUnverifiedMedicationData(context, reminder);
                    return;
                }
            } catch (RuntimeException missing) {
                alertUnverifiedMedicationData(context, reminder);
                return;
            }
            if (!canRenderMedication(event, schedule, patient)
                    || event.getId() == null
                    || displayedSchedules.put(event.getId(), schedule) != null) {
                alertUnverifiedMedicationData(context, reminder);
                return;
            }
            // The official-source association can change independently of
            // schedule and medicine. Never label a dose as officially
            // prescribed using another patient's imported treatment.
            if (schedule.isBoundToActiveMed()
                    || schedule.hasState(Schedule.ScheduleState.CREATED_FROM_OFFICIAL)) {
                final ActiveMedEntity source;
                try {
                    source = schedule.isBoundToActiveMed()
                            ? DB.healthcareProviderDB().activeMeds()
                                    .findById(schedule.getActiveMedId())
                            : null;
                } catch (RuntimeException unavailable) {
                    alertUnverifiedMedicationData(context, reminder);
                    return;
                }
                if (!canRenderOfficialSource(schedule, source)) {
                    alertUnverifiedMedicationData(context, reminder);
                    return;
                }
            }
            CharSequence line = verifiedNotificationLine(context, event, schedule, patient);
            if (line == null) {
                alertUnverifiedMedicationData(context, reminder);
                return;
            }
            style.addLine(line);
        }

        // Reuse the same bounded parser as actual repeat scheduling. Legacy
        // preferences may be null, malformed, negative or out of int range.
        int delayMinutes = ReminderTiming.repeatMinutes();

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

        // Bind the one-tap action to exactly the persisted dose set visible
        // above. Only a SHA-256 digest travels through PendingIntent; the
        // transaction later checks current SQLite before changing stock.
        final String visibleSnapshot = IntakeSnapshot.fingerprint(events, reminder, displayedSchedules);
        if (visibleSnapshot == null) {
            alertUnverifiedMedicationData(context, reminder);
            return;
        }
        if (intents != null) {
            // Delay and cancel are clinical decisions too: an old button
            // must not affect medication that appeared after this notice.
            intents.first.putExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT, visibleSnapshot);
            intents.second.putExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT, visibleSnapshot);
        }
        final Intent confirmAll = new Intent(context, AlarmReceiver.class);
        confirmAll.putExtra(IntentParams.EXTRA_ACTION, IntentParams.ACTION_ALARM_CONFIRM);
        confirmAll.putExtra(IntentParams.EXTRA_REMINDER_ID, reminder.getId());
        confirmAll.putExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT, visibleSnapshot);

        NotificationOptions options = new NotificationOptions();
        options.style = style;
        options.lost = lost;
        options.when = dateTime.getMillis();
        options.tag = makeTag(reminder);
        options.notificationNumber = events.size();
        options.picture = getLargeIcon(context.getResources(), reminder.getPatient());
        options.text = events.size() + " " + context.getString(R.string.home_menu_medicines).toLowerCase(Locale.getDefault());

        final Intent defaultIntent = new Intent(context, ConfirmActivity.class);
        defaultIntent.putExtra(IntentParams.EXTRA_ACTION, IntentParams.ACTION_ALARM_REMINDER);
        defaultIntent.putExtra(IntentParams.EXTRA_PATIENT_ID, patient.getId());
        defaultIntent.putExtra(IntentParams.EXTRA_DATETIME, dateTime.toString(ISODateTimeFormat.dateTimeNoMillis()));

        notify(context, reminder.getId(), title, intents, confirmAll, defaultIntent, options);
        showInsistentScreen(context, defaultIntent);
    }

    /**
     * Cancels any notifications of this type previously shown using
     */
    public static void cancel(final Context context, EventReminder e) {
        // A rolled-back/unsaved reminder has no notification identity.
        // Never dereference its missing SQLite primary key while cleaning up.
        if (e == null || e.getId() == null) {
            return;
        }
        final NotificationManagerCompat nm = NotificationManagerCompat.from(context);
        nm.cancel(makeTag(e), e.getId().intValue());
    }

    private static String makeTag(EventReminder reminder) {
        return "REMINDER-" + reminder.getId();
    }

    private static void showInsistentScreen(Context context, Intent i) {
        boolean insistentNotifications = NotificationFlagPolicy.configuredOrDefault(PreferenceKeys.SETTINGS_ALARM_INSISTENT, false);
        if (insistentNotifications) {
            Intent intent = new Intent(context, LockScreenAlarmActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra("target", i);
            context.startActivity(intent);
        }
    }

    private static void notify(final Context context, long reminderId, final String title,
                               Pair<Intent, Intent> actionIntents, Intent confirmIntent, Intent intent,
                               NotificationOptions options) {
        int id = (int) reminderId;

        boolean notifications = NotificationFlagPolicy.configuredOrDefault(PreferenceKeys.SETTINGS_ALARM_NOTIFICATIONS, true);


        // if notifications are disabled, exit
        if (!notifications) {
            return;
        }

        final Resources res = context.getResources();
        // prepare notification intents
        PendingIntent defaultIntent = PendingIntent.getActivity(
                context,
                id,
                identifyNotificationAction(intent, "OPEN", reminderId),
                PendingIntentFlags.immutable(PendingIntent.FLAG_UPDATE_CURRENT));
        PendingIntent delayIntent = null;
        PendingIntent cancelIntent = null;
        PendingIntent confirmAllIntent = null;

        if (!options.lost) {
            delayIntent = PendingIntent.getBroadcast(
                    context,
                    id,
                    identifyNotificationAction(actionIntents.first, "DELAY", reminderId),
                    PendingIntentFlags.immutable(PendingIntent.FLAG_UPDATE_CURRENT));
            cancelIntent = PendingIntent.getBroadcast(
                    context,
                    id,
                    identifyNotificationAction(actionIntents.second, "CANCEL", reminderId),
                    PendingIntentFlags.immutable(PendingIntent.FLAG_UPDATE_CURRENT));
            confirmAllIntent = PendingIntent.getBroadcast(
                    context,
                    id,
                    identifyNotificationAction(confirmIntent, "CONFIRM", reminderId),
                    PendingIntentFlags.immutable(PendingIntent.FLAG_UPDATE_CURRENT));
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
        boolean insistentNotifications = NotificationFlagPolicy.configuredOrDefault(PreferenceKeys.SETTINGS_ALARM_INSISTENT, false);

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
        // Historic/restored preferences sometimes contain a non-string
        // notification tone. Never let one malformed preference suppress a
        // time-critical medication reminder.
        String stored = null;
        try {
            stored = PreferenceUtils.getString(PreferenceKeys.SETTINGS_NOTIFICATION_TONE, null);
        } catch (ClassCastException malformed) {
            LogUtil.w(TAG, "Ignoring incompatible notification sound preference");
        }
        Uri systemTone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        Uri fallback = systemTone != null ? systemTone : Settings.System.DEFAULT_NOTIFICATION_URI;
        return NotificationSoundPolicy.safeNotificationTone(stored, fallback);
    }

    private static Bitmap getLargeIcon(Resources r, Patient p) {
        return BitmapFactory.decodeResource(r, AvatarMgr.res(p.getAvatar()));
    }


    private static void notify(final Context context, int id, final Notification notification, String tag) {
        if (!NotificationHelper.canPostNotifications(context)) {
            return;
        }
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
