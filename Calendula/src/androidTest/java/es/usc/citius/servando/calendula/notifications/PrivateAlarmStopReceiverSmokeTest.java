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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

/**
 * Validate registration and same-app delivery on every supported Android API.
 * Uses a unique test-only action; never starts, dismisses or updates an alarm.
 */
@RunWith(AndroidJUnit4.class)
public class PrivateAlarmStopReceiverSmokeTest {
    @Test
    public void notExportedAlarmReceiverCanReceiveSameAppSignalsOnSupportedApis()
            throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final String action = "es.usc.citius.calendula.TEST_NON_EXPORTED_ALARM_RECEIVER";
        final CountDownLatch received = new CountDownLatch(1);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context receivingContext, Intent intent) {
                if (action.equals(intent.getAction())) {
                    received.countDown();
                }
            }
        };
        boolean registered = false;
        try {
            ContextCompat.registerReceiver(context, receiver, new IntentFilter(action),
                    ContextCompat.RECEIVER_NOT_EXPORTED);
            registered = true;
            // Matching app package + UID. An unrelated package cannot use this
            // test to interact with the real medication STOP_SIGNAL receiver.
            context.sendBroadcast(new Intent(action).setPackage(context.getPackageName()));
            assertTrue("App-private signal must remain deliverable on this Android version",
                    received.await(10, TimeUnit.SECONDS));
        } finally {
            if (registered) {
                context.unregisterReceiver(receiver);
            }
        }
    }
}
