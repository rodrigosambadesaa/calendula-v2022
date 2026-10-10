@file:Suppress("DEPRECATION")

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
import es.usc.citius.servando.calendula.scheduling.model.EventReminder
import es.usc.citius.servando.calendula.scheduling.model.EventType
import es.usc.citius.servando.calendula.util.IntentParams
import es.usc.citius.servando.calendula.util.LogUtil

// JobIntentService remains the compatibility queue used by alarm/agenda dispatch. Migrating
// this flow to a modern scheduler is a separate behavioral change; keep deprecation suppression file-local.
class AlarmIntentService : JobIntentService() {

    companion object {

        private const val TAG = "AlarmIntentService"

        private const val JOB_ID = 1

        /**
         * Never let an unrelated reminder action confirm medication doses at
         * the same patient/time. Old notifications and corrupted imported
         * reminders must fail closed before invoking stock-changing code.
         */
        @JvmStatic
        fun isActionableMedicationReminder(reminder: EventReminder?): Boolean {
            val persistedId = reminder?.id ?: return false
            return persistedId > 0L &&
                reminder.eventType == EventType.MEDICATION_INTAKE &&
                reminder.patient?.id != null &&
                reminder.dateTime != null
        }

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
                if (Agenda.instance().cancelReminder(this, reminderId)) {
                    LogUtil.d(TAG, "Medication reminder cancelled after persisted change")
                    showToast(getString(R.string.reminder_cancelled_message))
                } else {
                    LogUtil.w(TAG, "Ignoring cancellation for missing or stale reminder")
                }
            }
            IntentParams.ACTION_ALARM_DELAY -> {
                // Tell agenda to delay the reminder
                val reminderId = intent.getLongExtra(IntentParams.EXTRA_REMINDER_ID, -1)
                if (Agenda.instance().delayReminder(this, reminderId)) {
                    showToast(getString(R.string.alarm_delayed_notification_message))
                } else {
                    LogUtil.w(TAG, "Ignoring delay for missing or inactive reminder")
                }
            }
            IntentParams.ACTION_ALARM_CONFIRM -> {
                // Tell agenda to confirm the reminder
                val reminderId = intent.getLongExtra(IntentParams.EXTRA_REMINDER_ID, -1)
                if (reminderId <= 0L) {
                    LogUtil.w(TAG, "Ignoring confirmation without valid reminder ID")
                    return
                }
                val reminder = DB.eventReminders().findById(reminderId)
                if (reminder != null && isActionableMedicationReminder(reminder)) {
                    val confirmed = ScheduleUtils.instance()
                        .checkIntakeEvents(this, reminder.patient, reminder.dateTime)
                    if (confirmed > 0) {
                        showToast(getString(R.string.all_meds_taken))
                    } else {
                        LogUtil.w(TAG, "No pending medication intakes to confirm")
                    }
                } else {
                    // This may be a stale notification whose SQLite row was
                    // already cleared. Never claim that medicine was taken.
                    LogUtil.w(TAG, "Confirmation ignored: missing or invalid medication reminder")
                }
            }
            else -> LogUtil.w(TAG, "Unknown action '$action', request will be ignored")
        }
    }

    private fun showToast(message: String) {
        handler.post { Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show() }
    }

}
