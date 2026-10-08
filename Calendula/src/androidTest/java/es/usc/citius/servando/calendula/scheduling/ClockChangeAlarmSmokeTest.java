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

import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.util.IntentParams;
import es.usc.citius.servando.calendula.util.PendingIntentFlags;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Real Android device smoke, synthetic clock-change broadcasts only.
 * The clock is not actually modified; no patient records are edited.
 */
@RunWith(AndroidJUnit4.class)
public class ClockChangeAlarmSmokeTest {

    private static PendingIntent dailyToken(Context context, int flags) {
        Intent daily = new Intent(context, AlarmReceiver.class);
        daily.putExtra(IntentParams.EXTRA_ACTION, IntentParams.ACTION_DAILY_UPDATE);
        return PendingIntent.getBroadcast(context, IntentParams.DAILY_UPDATE_ID,
                daily, PendingIntentFlags.immutable(flags));
    }

    @Test
    public void clockAndTimezoneChangeReRegisterDailyAgendaMaintenance() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        for (String action : new String[]{
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED}) {
            PendingIntent previous = dailyToken(context, PendingIntent.FLAG_NO_CREATE);
            if (previous != null) {
                previous.cancel();
            }
            assertNull("Synthetic test must begin without a daily token",
                    dailyToken(context, PendingIntent.FLAG_NO_CREATE));

            // Exercise the actual receiver against the disposable emulator app
            // without altering Android time, network, or medical records.
            new BootReceiver().onReceive(context, new Intent(action));
            assertNotNull("Clock changes must restore daily maintenance",
                    dailyToken(context, PendingIntent.FLAG_NO_CREATE));
        }
        // Keep the last daily maintenance alarm registered.
    }
}
