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

package es.usc.citius.servando.calendula.healthcareprovider.notifications;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.provider.Settings;
import androidx.core.app.NotificationCompat;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;

import es.usc.citius.servando.calendula.HomePagerActivity;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.notifications.NotificationHelper;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.healthcareprovider.jobs.UpdateSummary;
import es.usc.citius.servando.calendula.util.AvatarMgr;
import es.usc.citius.servando.calendula.util.IntentParams;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;


public class ActiveMedNotification {


    private static final int NOTIFICATION_ID = "ActiveMedNotification".hashCode();
    private static final String NOTIFICATION_TAG = "ActiveMedNotification";
    private static final String ADD_SYMBOL = "+";
    private static final String UPDATE_SYMBOL = "↻";
    private static final String DELETE_SYMBOL = "x";

    public static void show(Context context, Patient p, UpdateSummary summary) {

        boolean insistentNotifications = PreferenceUtils.getBoolean(PreferenceKeys.SETTINGS_ALARM_INSISTENT, false);

        Intent intent = new Intent(context, HomePagerActivity.class);
        intent.putExtra(IntentParams.EXTRA_ACTION, IntentParams.ACTION_SHOW_ACTIVE_MEDS);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, NOTIFICATION_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();

        String summaryText;
        if (summary.hasDeleted() && summary.hasUpdated() && summary.hasNew()) {
            summaryText = context.getString(R.string.aml_updated_summary, summary.newCount(), summary.updatedCount(), summary.deletedCount());
        } else if (summary.hasUpdated() && summary.hasNew()) {
            summaryText = context.getString(R.string.aml_updated_new_and_updated_summary, summary.newCount(), summary.updatedCount());
        } else if (summary.hasNew()) {
            summaryText = context.getString(R.string.aml_updated_new_summary, summary.newCount());
        } else if (summary.hasUpdated()) {
            summaryText = context.getString(R.string.aml_updated_updated_summary, summary.updatedCount());
        } else {
            summaryText = context.getString(R.string.aml_updated_notification_text);
        }

        StringBuilder summaryStr = new StringBuilder();
        for (int i = 0; i < summary.getCreated().size(); i++) {
            String line = StringUtils.capitalize(summary.getCreated().get(i).getDefaultDisplay().toLowerCase());
            summaryStr.append("• ").append(context.getString(R.string.active_med_notification_new)).append(" ").append(line).append("\n\n");
            inboxStyle.addLine(ADD_SYMBOL + " " + line);
        }
        for (int i = 0; i < summary.getUpdated().size(); i++) {
            String line = StringUtils.capitalize(summary.getUpdated().get(i).getDefaultDisplay().toLowerCase());
            summaryStr.append("• ").append(context.getString(R.string.active_med_notification_updated)).append(" ").append(line).append("\n\n");
            inboxStyle.addLine(UPDATE_SYMBOL + " " + line);
        }
        for (int i = 0; i < summary.getDeleted().size(); i++) {
            String line = StringUtils.capitalize(summary.getDeleted().get(i).getDefaultDisplay().toLowerCase());
            summaryStr.append("• ").append(context.getString(R.string.active_med_notification_deleted)).append(" ").append(line).append("\n\n");
            inboxStyle.addLine(DELETE_SYMBOL + " " + line);
        }
        inboxStyle.setBigContentTitle(context.getString(R.string.aml_updated_notification_title));
        inboxStyle.setSummaryText(summaryText);

        PreferenceUtils.edit().putString(PreferenceKeys.REMOTE_LAST_GOOD_UPDATE_SUMMARY.key(), summaryStr.toString()).apply();

        final NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NotificationHelper.CHANNEL_MEDS_ID)

                .setDefaults(Notification.DEFAULT_ALL)
                .setSmallIcon(R.drawable.ic_active_med_update_w)
                .setContentTitle(context.getString(R.string.aml_updated_notification_title))
                .setContentText(context.getString(R.string.aml_updated_notification_text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setLargeIcon(getLargeIcon(context.getResources(), p))
                .setTicker(summaryText)
                .setNumber(summary.changesCount())
                .setContentIntent(pendingIntent)
                .setStyle(inboxStyle)
                .setAutoCancel(true)
                .setWhen(DateTime.now().getMillis());


        if (!insistentNotifications) {
            // if insistent is enabled, an activity with vibration will start
            builder.setVibrate(new long[]{1000, 200, 100, 300, 200, 200, 100, 300, 200, 200, 100, 300, 1000}).setSound(Settings.System.DEFAULT_NOTIFICATION_URI);
        }

        Notification n = builder.build();
        n.defaults = 0;
        n.ledARGB = 0x00ffa500;
        n.ledOnMS = 1000;
        n.ledOffMS = 2000;

        notify(context, n);
    }

    /**
     * Cancels any notifications of this type previously shown using
     */
    @TargetApi(Build.VERSION_CODES.ECLAIR)
    public static void cancel(final Context context) {
        final NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(NOTIFICATION_TAG, NOTIFICATION_ID);
    }

    private static Bitmap getLargeIcon(Resources r, Patient p) {
        return BitmapFactory.decodeResource(r, AvatarMgr.res(p.getAvatar()));
    }

    @TargetApi(Build.VERSION_CODES.ECLAIR)
    private static void notify(final Context context, final Notification notification) {
        final NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_TAG, NOTIFICATION_ID, notification);
    }


}
