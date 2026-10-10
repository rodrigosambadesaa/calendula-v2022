/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling

import es.usc.citius.servando.calendula.util.IntentParams
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
    fun unknownActionsNeverStartAlarmWork() {
        assertFalse(AlarmReceiver.isValidAlarmDispatch(null, 10L))
        assertFalse(AlarmReceiver.isValidAlarmDispatch("", 10L))
        assertFalse(AlarmReceiver.isValidAlarmDispatch(
            IntentParams.ACTION_CREATE_REMINDER, 10L))
        assertFalse(AlarmReceiver.isValidAlarmDispatch(
            IntentParams.ACTION_SHOW_ACTIVE_MEDS, 10L))
    }
}
