/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling

import android.content.Intent
import es.usc.citius.servando.calendula.util.IntentParams
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
    fun notificationConfirmationPreservesOnlyValidatedOpaqueConsent() {
        val context = RuntimeEnvironment.getApplication()
        val fingerprint = "a".repeat(64)
        val incoming = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(IntentParams.EXTRA_ACTION, IntentParams.ACTION_ALARM_CONFIRM)
            putExtra(IntentParams.EXTRA_REMINDER_ID, 123L)
            putExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT, fingerprint)
            putExtra(IntentParams.EXTRA_PATIENT_ID, 999L)
            putExtra(IntentParams.EXTRA_DATETIME, "private clinical timestamp")
        }

        val work = AlarmReceiver.validatedServiceIntent(context, incoming)
        assertNotNull(work)
        assertEquals(AlarmIntentService::class.java.name, work!!.component!!.className)
        assertEquals(IntentParams.ACTION_ALARM_CONFIRM,
            work.getStringExtra(IntentParams.EXTRA_ACTION))
        assertEquals(123L, work.getLongExtra(IntentParams.EXTRA_REMINDER_ID, -1L))
        assertEquals(fingerprint, work.getStringExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT))
        assertFalse("No unrelated patient identifiers in the background queue",
            work.hasExtra(IntentParams.EXTRA_PATIENT_ID))
        assertFalse("No clinical timestamp in the queued intent",
            work.hasExtra(IntentParams.EXTRA_DATETIME))
    }

    @Test
    fun legacyOrMalformedOneTapActionsCannotEnterWorkQueue() {
        val context = RuntimeEnvironment.getApplication()
        val action = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(IntentParams.EXTRA_ACTION, IntentParams.ACTION_ALARM_CONFIRM)
            putExtra(IntentParams.EXTRA_REMINDER_ID, 987L)
        }
        assertNull("An old notification did not capture the displayed doses",
            AlarmReceiver.validatedServiceIntent(context, action))

        for (malformed in listOf("", "not-a-digest", "A".repeat(64),
                "z".repeat(64), "a".repeat(63), "a".repeat(65))) {
            action.putExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT, malformed)
            assertNull("Malformed digest must not reach intake persistence",
                AlarmReceiver.validatedServiceIntent(context, action))
        }
        action.putExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT, "0".repeat(64))
        action.putExtra(IntentParams.EXTRA_REMINDER_ID, -1L)
        assertNull(AlarmReceiver.validatedServiceIntent(context, action))
        action.putExtra(IntentParams.EXTRA_REMINDER_ID, 0L)
        assertNull(AlarmReceiver.validatedServiceIntent(context, action))
    }

    @Test
    fun wrongTypedExtrasCannotCrashOrConfirmMedication() {
        val context = RuntimeEnvironment.getApplication()
        val action = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(IntentParams.EXTRA_ACTION, IntentParams.ACTION_ALARM_CONFIRM)
            putExtra(IntentParams.EXTRA_REMINDER_ID, 987L)
            putExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT, 123456)
        }
        assertNull(AlarmReceiver.validatedServiceIntent(context, action))
    }

    @Test
    fun unrelatedAlarmWorkCannotCarryForwardMedicationConsent() {
        val context = RuntimeEnvironment.getApplication()
        for (action in listOf(IntentParams.ACTION_ALARM_DELAY,
                IntentParams.ACTION_ALARM_CANCEL, IntentParams.ACTION_ALARM_REMINDER)) {
            val incoming = Intent(context, AlarmReceiver::class.java).apply {
                putExtra(IntentParams.EXTRA_ACTION, action)
                putExtra(IntentParams.EXTRA_REMINDER_ID, 123L)
                putExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT, "a".repeat(64))
            }
            val work = AlarmReceiver.validatedServiceIntent(context, incoming)
            assertNotNull(work)
            assertEquals(action, work!!.getStringExtra(IntentParams.EXTRA_ACTION))
            assertFalse(work.hasExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT))
        }
        val daily = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(IntentParams.EXTRA_ACTION, IntentParams.ACTION_DAILY_UPDATE)
        }
        val dailyWork = AlarmReceiver.validatedServiceIntent(context, daily)
        assertNotNull("Local-midnight recovery must not require a dose snapshot", dailyWork)
        assertFalse(dailyWork!!.hasExtra(IntentParams.EXTRA_INTAKE_SNAPSHOT))
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
