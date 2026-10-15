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

package es.usc.citius.servando.calendula.scheduling;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.activities.CalendarActivity;
import es.usc.citius.servando.calendula.activities.PickupNotification;

/**
 * This class receives our pickup check alarms
 */
public class PickupAlarmReceiver extends BroadcastReceiver {

    /**
     * Android may redeliver a legacy or malformed alarm Intent after an
     * upgrade. Only the explicit pickup-check action may post a notification.
     */
    static boolean isPickupCheck(Intent intent) {
        return intent != null
                && intent.getIntExtra(CalendulaApp.INTENT_EXTRA_ACTION, -1)
                        == CalendulaApp.ACTION_CHECK_PICKUPS_ALARM;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || CalendulaApp.disableReceivers || !isPickupCheck(intent)) {
            return;
        }
        showNotification(context);
    }

    private void showNotification(Context ctx) {
        // Generic, localized copy avoids disclosing medicine or patient names
        // on a shared device's lock screen.
        Intent openCalendar = new Intent(ctx, CalendarActivity.class);
        openCalendar.putExtra(CalendulaApp.INTENT_EXTRA_ACTION,
                CalendarActivity.ACTION_SHOW_REMINDERS);
        PickupNotification.notify(ctx,
                ctx.getString(R.string.pickup_alarm_title),
                ctx.getString(R.string.pickup_alarm_description),
                openCalendar);
    }

}