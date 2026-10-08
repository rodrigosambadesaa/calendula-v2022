/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.util.IntentParams;
import es.usc.citius.servando.calendula.util.PendingIntentFlags;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Android API 23/33/36: real PendingIntent re-registration, plus calendar
 * arithmetic. Does not emit a broadcast or access patient databases.
 */
@RunWith(AndroidJUnit4.class)
public class DailyMidnightAlarmSmokeTest {
    private static final DateTimeZone MADRID = DateTimeZone.forID("Europe/Madrid");

    @Test
    public void dailyAlarmTokenExistsAfterOneShotRegistration() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // Replace any previous daily registration with the current one-shot.
        Agenda.instance().setDailyUpdateAlarm(context);
        Intent daily = new Intent(context, AlarmReceiver.class);
        daily.putExtra(IntentParams.EXTRA_ACTION, IntentParams.ACTION_DAILY_UPDATE);
        PendingIntent existing = PendingIntent.getBroadcast(context,
                IntentParams.DAILY_UPDATE_ID, daily,
                PendingIntentFlags.immutable(PendingIntent.FLAG_NO_CREATE));
        assertNotNull("Daily calendar refresh must remain registered", existing);
        // Leave the daily maintenance token registered for subsequent app work.
    }

    @Test
    public void dstDaysAreNotAssumedToLastExactlyTwentyFourHours() {
        long springStart = new DateTime(2026, 3, 29, 0, 0, MADRID).getMillis();
        long springNext = Agenda.nextDailyUpdateMillis(new DateTime(springStart, MADRID));
        assertEquals(23L * 60 * 60 * 1000, springNext - springStart);

        long fallStart = new DateTime(2026, 10, 25, 0, 0, MADRID).getMillis();
        long fallNext = Agenda.nextDailyUpdateMillis(new DateTime(fallStart, MADRID));
        assertEquals(25L * 60 * 60 * 1000, fallNext - fallStart);
    }
}
