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
        private val SHA256_HEX = Regex("[0-9a-f]{64}")

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
         * Build precisely the work request that may reach medication
         * persistence. Legacy, malformed or absent one-tap consent must be
         * rejected before any JobIntentService work is enqueued.
         */
        @JvmStatic
        fun serviceWorkIntent(context: Context, broadcast: Intent): Intent? {
            val action = broadcast.getStringExtra(IntentParams.EXTRA_ACTION)
            val reminderId = broadcast.getLongExtra(IntentParams.EXTRA_REMINDER_ID, -1L)
            if (!isValidAlarmDispatch(action, reminderId)) return null
            val work = Intent(context, AlarmIntentService::class.java)
            work.putExtra(IntentParams.EXTRA_ACTION, action)
            work.putExtra(IntentParams.EXTRA_REMINDER_ID, reminderId)
            if (action == IntentParams.ACTION_ALARM_CONFIRM) {
                val fingerprint =
                    broadcast.getStringExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT)
                if (fingerprint == null || !SHA256_HEX.matches(fingerprint)) return null
                work.putExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT, fingerprint)
            }
            return work
        }
    }

    override fun onReceive(context: Context, intent: Intent) {

        if (CalendulaApp.disableReceivers) {
            return
        }

        val work = serviceWorkIntent(context, intent)
        if (work == null) {
            LogUtil.w(TAG, "Ignoring unsupported or stale notification alarm broadcast")
            return
        }
        val action = work.getStringExtra(IntentParams.EXTRA_ACTION)
        LogUtil.d(TAG, "Alarm received with action $action")

        if (action == IntentParams.ACTION_DAILY_UPDATE) {
            // One-shot daily refresh: re-arm the next LOCAL midnight before
            // handing work to the service, so process death during dispatch
            // cannot silently stop all future daily agenda updates.
            Agenda.instance().setDailyUpdateAlarm(context)
        }

        // Queue only the validated, minimal work request above; do not
        // forward arbitrary caller-supplied patient or medicine extras.
        AlarmIntentService.enqueueWork(context, work)
    }

}