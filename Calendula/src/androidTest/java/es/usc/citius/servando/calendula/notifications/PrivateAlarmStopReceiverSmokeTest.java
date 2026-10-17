/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.core.content.ContextCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertTrue;

/** Disposable framework registration only; never starts or stops a real alarm. */
@RunWith(AndroidJUnit4.class)
public class PrivateAlarmStopReceiverSmokeTest {
    @Test
    public void notExportedAlarmReceiverCanRegisterOnEverySupportedAndroidApi() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context receivingContext, Intent intent) {
                // No patient data and no actual alarm side effects.
            }
        };
        IntentFilter filter = new IntentFilter(
                "es.usc.citius.calendula.TEST_NON_EXPORTED_ALARM_RECEIVER");
        BroadcastReceiver registered = receiver;
        boolean installed = false;
        try {
            ContextCompat.registerReceiver(context, registered, filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED);
            installed = true;
            assertTrue("Receiver registration must complete on this platform", installed);
        } finally {
            if (installed) {
                context.unregisterReceiver(registered);
            }
        }
    }
}
