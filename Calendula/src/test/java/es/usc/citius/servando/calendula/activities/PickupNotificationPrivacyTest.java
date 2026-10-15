package es.usc.citius.servando.calendula.activities;

import android.app.Notification;
import android.content.Context;

import androidx.core.app.NotificationCompat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.notifications.NotificationHelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PickupNotificationPrivacyTest {
    @Test
    public void publicLockscreenPreviewNeverContainsPickupOrPatientDetails() {
        Context context = RuntimeEnvironment.getApplication();
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, NotificationHelper.CHANNEL_DEFAULT_ID)
                        .setSmallIcon(R.drawable.ic_event_available_white_48dp)
                        .setContentTitle("patient and medicine pickup date")
                        .setContentText("private clinical description");
        PickupNotification.applyLockscreenPrivacy(context, builder);
        Notification privateNotice = builder.build();

        assertEquals(Notification.VISIBILITY_PRIVATE, privateNotice.visibility);
        assertNotNull(privateNotice.publicVersion);
        assertEquals(context.getString(R.string.app_name),
                privateNotice.publicVersion.extras.getString(Notification.EXTRA_TITLE));
        assertEquals(context.getString(R.string.app_name),
                privateNotice.publicVersion.extras.getString(Notification.EXTRA_TEXT));
        assertEquals(context.getString(R.string.app_name), privateNotice.tickerText.toString());
        assertNull("Public lockscreen must not contain a profile thumbnail",
                privateNotice.publicVersion.largeIcon);
    }
}
