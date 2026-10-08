/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import android.app.AlarmManager;
import android.app.PendingIntent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class AgendaAlarmSchedulingTest {

    private static final long TRIGGER_MILLIS = 1700000000000L;

    @Test
    public void exactAccessSchedulesExactReminder() {
        AlarmManager alarms = mock(AlarmManager.class);
        PendingIntent operation = mock(PendingIntent.class);
        when(alarms.canScheduleExactAlarms()).thenReturn(true);

        Agenda.scheduleAlarm(alarms, TRIGGER_MILLIS, operation);

        verify(alarms).setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, TRIGGER_MILLIS, operation);
        verify(alarms, never()).setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, TRIGGER_MILLIS, operation);
    }

    @Test
    public void deniedExactAccessSchedulesInexactReminder() {
        AlarmManager alarms = mock(AlarmManager.class);
        PendingIntent operation = mock(PendingIntent.class);
        when(alarms.canScheduleExactAlarms()).thenReturn(false);

        Agenda.scheduleAlarm(alarms, TRIGGER_MILLIS, operation);

        verify(alarms, never()).setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, TRIGGER_MILLIS, operation);
        verify(alarms).setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, TRIGGER_MILLIS, operation);
    }

    @Test
    public void permissionRevokedDuringExactSchedulingFallsBackInsteadOfCrashing() {
        AlarmManager alarms = mock(AlarmManager.class);
        PendingIntent operation = mock(PendingIntent.class);
        when(alarms.canScheduleExactAlarms()).thenReturn(true);
        doThrow(new SecurityException("exact alarm access revoked"))
                .when(alarms).setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, TRIGGER_MILLIS, operation);

        Agenda.scheduleAlarm(alarms, TRIGGER_MILLIS, operation);

        verify(alarms).setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, TRIGGER_MILLIS, operation);
        verify(alarms).setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, TRIGGER_MILLIS, operation);
    }
}
