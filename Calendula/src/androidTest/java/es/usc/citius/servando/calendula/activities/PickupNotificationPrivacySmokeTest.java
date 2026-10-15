package es.usc.citius.servando.calendula.activities;

import android.app.Notification;
import android.content.Context;

import androidx.core.app.NotificationCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.notifications.NotificationHelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public class PickupNotificationPrivacySmokeTest {
    @Test
    public void publicPickupPreviewIsGenericOnActualAndroid() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, NotificationHelper.CHANNEL_DEFAULT_ID)
                        .setSmallIcon(R.drawable.ic_event_available_white_48dp)
                        .setContentText("private synthetic pickup details");
        PickupNotification.applyLockscreenPrivacy(context, builder);
        Notification notification = builder.build();
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility);
        assertNotNull(notification.publicVersion);
        assertEquals(context.getString(R.string.app_name),
                notification.publicVersion.extras.getString(Notification.EXTRA_TEXT));
        assertEquals(context.getString(R.string.app_name),
                notification.tickerText.toString());
    }
}
