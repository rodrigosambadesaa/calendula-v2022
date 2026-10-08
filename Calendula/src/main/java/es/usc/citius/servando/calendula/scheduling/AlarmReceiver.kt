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

package es.usc.citius.servando.calendula.scheduling


import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import es.usc.citius.servando.calendula.CalendulaApp
import es.usc.citius.servando.calendula.util.IntentParams
import es.usc.citius.servando.calendula.util.LogUtil


/**
 * This class receives our routine alarms
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {

        if (CalendulaApp.disableReceivers) {
            return
        }

        val action = intent.getStringExtra(IntentParams.EXTRA_ACTION)
        LogUtil.d(TAG, "Alarm received with action $action")

        if (action == IntentParams.ACTION_DAILY_UPDATE) {
            // One-shot daily refresh: re-arm the next LOCAL midnight before
            // handing work to the service, so process death during dispatch
            // cannot silently stop all future daily agenda updates.
            Agenda.instance().setDailyUpdateAlarm(context)
        }

        // call service
        val serviceIntent = Intent(context, AlarmIntentService::class.java)
        serviceIntent.putExtra(IntentParams.EXTRA_ACTION, action)
        serviceIntent.putExtra(
            IntentParams.EXTRA_REMINDER_ID,
            intent.getLongExtra(IntentParams.EXTRA_REMINDER_ID, -1)
        )
        AlarmIntentService.enqueueWork(context, serviceIntent)
    }

}