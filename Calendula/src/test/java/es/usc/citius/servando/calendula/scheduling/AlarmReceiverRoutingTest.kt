/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling

import es.usc.citius.servando.calendula.util.IntentParams
import android.content.Intent
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Pure routing validation: no alarms, database or patient records. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AlarmReceiverRoutingTest {

    @Test
    fun dailyRefreshDoesNotNeedAnIntakeRowId() {
        assertTrue(AlarmReceiver.isValidAlarmDispatch(IntentParams.ACTION_DAILY_UPDATE, -1L))
        assertTrue(AlarmReceiver.isValidAlarmDispatch(IntentParams.ACTION_DAILY_UPDATE, 0L))
    }

    @Test
    fun medicationCallbacksNeedValidPersistedReminderIds() {
        val actions = listOf(
            IntentParams.ACTION_ALARM_REMINDER,
            IntentParams.ACTION_ALARM_DELAY,
            IntentParams.ACTION_ALARM_CANCEL,
            IntentParams.ACTION_ALARM_CONFIRM
        )
        actions.forEach { action ->
            assertTrue(AlarmReceiver.isValidAlarmDispatch(action, 10L))
            assertFalse(AlarmReceiver.isValidAlarmDispatch(action, -1L))
            assertFalse(AlarmReceiver.isValidAlarmDispatch(action, 0L))
        }
    }

    @Test
    fun actualNotificationWorkForwardingKeepsOnlyValidOpaqueConsent() {
        val context = RuntimeEnvironment.getApplication()
        val digest = "a".repeat(64)
        val broadcast = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(IntentParams.EXTRA_ACTION, IntentParams.ACTION_ALARM_CONFIRM)
            putExtra(IntentParams.EXTRA_REMINDER_ID, 71001L)
            putExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT, digest)
            putExtra(IntentParams.EXTRA_PATIENT_ID, 9999L)
        }
        val work = AlarmReceiver.serviceWorkIntent(context, broadcast)
        assertNotNull(work)
        assertEquals(AlarmIntentService::class.java.name, work!!.component!!.className)
        assertEquals(IntentParams.ACTION_ALARM_CONFIRM,
            work.getStringExtra(IntentParams.EXTRA_ACTION))
        assertEquals(71001L, work.getLongExtra(IntentParams.EXTRA_REMINDER_ID, -1L))
        assertEquals(digest, work.getStringExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT))
        assertFalse("A caller's patient identifier is never forwarded",
            work.hasExtra(IntentParams.EXTRA_PATIENT_ID))

        // No-snapshot, malformed, empty and future/unsupported formats must
        // never reach the worker that can debit medication inventory.
        broadcast.removeExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT)
        assertNull(AlarmReceiver.serviceWorkIntent(context, broadcast))
        listOf("", "legacy", "a".repeat(63), "z".repeat(64), "A".repeat(64))
            .forEach { bad ->
                broadcast.putExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT, bad)
                assertNull(AlarmReceiver.serviceWorkIntent(context, broadcast))
            }
        broadcast.putExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT, digest)
        broadcast.putExtra(IntentParams.EXTRA_REMINDER_ID, 0L)
        assertNull(AlarmReceiver.serviceWorkIntent(context, broadcast))
    }

    @Test
    fun nonConfirmationAlarmsStillPreserveLegacyRouting() {
        val context = RuntimeEnvironment.getApplication()
        listOf(IntentParams.ACTION_ALARM_REMINDER,
            IntentParams.ACTION_ALARM_DELAY,
            IntentParams.ACTION_ALARM_CANCEL).forEach { action ->
            val broadcast = Intent(context, AlarmReceiver::class.java)
                .putExtra(IntentParams.EXTRA_ACTION, action)
                .putExtra(IntentParams.EXTRA_REMINDER_ID, 71001L)
            val work = AlarmReceiver.serviceWorkIntent(context, broadcast)
            assertNotNull(work)
            assertEquals(action, work!!.getStringExtra(IntentParams.EXTRA_ACTION))
            assertFalse(work.hasExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT))
        }
        val daily = Intent(context, AlarmReceiver::class.java)
            .putExtra(IntentParams.EXTRA_ACTION, IntentParams.ACTION_DAILY_UPDATE)
        val work = AlarmReceiver.serviceWorkIntent(context, daily)
        assertNotNull(work)
        assertEquals(IntentParams.ACTION_DAILY_UPDATE,
            work!!.getStringExtra(IntentParams.EXTRA_ACTION))
    }

    @Test
    fun unknownActionsNeverStartAlarmWork() {
        assertFalse(AlarmReceiver.isValidAlarmDispatch(null, 10L))
        assertFalse(AlarmReceiver.isValidAlarmDispatch("", 10L))
        assertFalse(AlarmReceiver.isValidAlarmDispatch(
            IntentParams.ACTION_CREATE_REMINDER, 10L))
        assertFalse(AlarmReceiver.isValidAlarmDispatch(
            IntentParams.ACTION_SHOW_ACTIVE_MEDS, 10L))
    }
}
