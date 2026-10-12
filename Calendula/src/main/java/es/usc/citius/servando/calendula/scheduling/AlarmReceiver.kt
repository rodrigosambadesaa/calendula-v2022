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

        /**
         * Legacy or malformed PendingIntents must never enqueue medication
         * work or cause an invalid broadcast to reset the daily alarm.
         * Daily agenda refresh intentionally has no medication row ID.
         */
        @JvmStatic
        fun isValidAlarmDispatch(action: String?, reminderId: Long): Boolean {
            if (action == IntentParams.ACTION_DAILY_UPDATE) return true
            if (reminderId <= 0L) return false
            return when (action) {
                IntentParams.ACTION_ALARM_REMINDER,
                IntentParams.ACTION_ALARM_DELAY,
                IntentParams.ACTION_ALARM_CANCEL,
                IntentParams.ACTION_ALARM_CONFIRM -> true
                else -> false
            }
        }

        /**
         * Build exactly the work that the service is allowed to receive.
         * Reject legacy one-tap confirmation broadcasts at the boundary,
         * before they enter a persistent JobIntentService queue. No clinical
         * names, patient IDs or dose amounts are forwarded as extras.
         */
        @JvmStatic
        fun validatedServiceIntent(context: Context, incoming: Intent?): Intent? {
            if (incoming == null) return null
            return try {
                val action = incoming.getStringExtra(IntentParams.EXTRA_ACTION)
                val reminderId = incoming.getLongExtra(IntentParams.EXTRA_REMINDER_ID, -1L)
                if (!isValidAlarmDispatch(action, reminderId)) return null

                val snapshot = if (action == IntentParams.ACTION_ALARM_CONFIRM) {
                    incoming.getStringExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT)
                } else {
                    null
                }
                if (action == IntentParams.ACTION_ALARM_CONFIRM &&
                    (snapshot == null || snapshot.length != 64 ||
                        !snapshot.all { it in '0'..'9' || it in 'a'..'f' })
                ) {
                    return null
                }

                Intent(context, AlarmIntentService::class.java).apply {
                    putExtra(IntentParams.EXTRA_ACTION, action)
                    putExtra(IntentParams.EXTRA_REMINDER_ID, reminderId)
                    if (snapshot != null) {
                        putExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT, snapshot)
                    }
                }
            } catch (malformed: RuntimeException) {
                // Legacy PendingIntents can contain a wrong-typed Bundle
                // field. Never crash or enqueue medication work for them.
                null
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (CalendulaApp.disableReceivers) return

        val serviceIntent = validatedServiceIntent(context, intent)
        if (serviceIntent == null) {
            LogUtil.w(TAG, "Ignoring unsupported, stale or malformed alarm broadcast")
            return
        }
        val action = serviceIntent.getStringExtra(IntentParams.EXTRA_ACTION)
        LogUtil.d(TAG, "Alarm received with action $action")

        if (action == IntentParams.ACTION_DAILY_UPDATE) {
            // One-shot daily refresh: re-arm the next LOCAL midnight before
            // handing work to the service, so process death during dispatch
            // cannot silently stop all future daily agenda updates.
            Agenda.instance().setDailyUpdateAlarm(context)
        }

        AlarmIntentService.enqueueWork(context, serviceIntent)
    }

}