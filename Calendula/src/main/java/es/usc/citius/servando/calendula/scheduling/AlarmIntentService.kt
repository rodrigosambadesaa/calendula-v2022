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

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.app.JobIntentService
import android.widget.Toast

import es.usc.citius.servando.calendula.R
import es.usc.citius.servando.calendula.database.DB
import es.usc.citius.servando.calendula.persistence.ScheduleUtils
import es.usc.citius.servando.calendula.util.IntentParams
import es.usc.citius.servando.calendula.util.LogUtil

class AlarmIntentService : JobIntentService() {

    companion object {

        private const val TAG = "AlarmIntentService"

        private const val JOB_ID = 1

        @JvmStatic
        fun enqueueWork(context: Context, work: Intent) {
            JobIntentService.enqueueWork(context, AlarmIntentService::class.java, JOB_ID, work)
        }
    }


    internal var handler: Handler = Handler(Looper.getMainLooper())

    override fun onHandleWork(intent: Intent) {

        val action = intent.getStringExtra(IntentParams.EXTRA_ACTION)
        LogUtil.d(TAG, "AlarmIntentService request with action '$action'")

        when (action) {
            IntentParams.ACTION_DAILY_UPDATE -> {
                // Update daily agenda
                LogUtil.d(TAG, "Update daily agenda")
                Agenda.instance().onDailyUpdate(this)
            }
            IntentParams.ACTION_ALARM_REMINDER -> {
                // send reminder to agenda
                val reminderId = intent.getLongExtra(IntentParams.EXTRA_REMINDER_ID, -1)
                Agenda.instance().onReceiveAlarm(this, reminderId)
                LogUtil.d(TAG, "Send reminder to agenda")
            }
            IntentParams.ACTION_ALARM_CANCEL -> {
                // Tell agenda to cancel the reminder
                val reminderId = intent.getLongExtra(IntentParams.EXTRA_REMINDER_ID, -1)
                Agenda.instance().cancelReminder(this, reminderId)
                LogUtil.d(TAG, "Tell agenda to cancel the reminder")
                showToast(getString(R.string.reminder_cancelled_message))
            }
            IntentParams.ACTION_ALARM_DELAY -> {
                // Tell agenda to delay the reminder
                val reminderId = intent.getLongExtra(IntentParams.EXTRA_REMINDER_ID, -1)
                Agenda.instance().delayReminder(this, reminderId)
                showToast(getString(R.string.alarm_delayed_notification_message))
            }
            IntentParams.ACTION_ALARM_CONFIRM -> {
                // Tell agenda to confirm the reminder
                val reminderId = intent.getLongExtra(IntentParams.EXTRA_REMINDER_ID, -1)
                val reminder = DB.eventReminders().findById(reminderId)
                if (reminder != null) {
                    ScheduleUtils.instance()
                        .checkIntakeEvents(this, reminder.patient, reminder.dateTime)
                }
                showToast(getString(R.string.all_meds_taken))
            }
            else -> LogUtil.w(TAG, "Unknown action '$action', request will be ignored")
        }
    }

    private fun showToast(message: String) {
        handler.post { Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show() }
    }

}
