package es.usc.citius.servando.calendula.healthcareprovider.notifications;

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
public class ActiveMedLockscreenPrivacySmokeTest {
    @Test
    public void medicationNamesCannotAppearInPublicNotificationPreview() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, NotificationHelper.CHANNEL_MEDS_ID)
                        .setSmallIcon(R.drawable.ic_active_med_update_w)
                        .setContentTitle("synthetic sensitive title");
        ActiveMedNotification.applyLockscreenPrivacy(context, builder);
        Notification notice = builder.build();
        assertEquals(Notification.VISIBILITY_PRIVATE, notice.visibility);
        assertNotNull(notice.publicVersion);
        assertEquals(context.getString(R.string.app_name),
                notice.publicVersion.extras.getString(Notification.EXTRA_TITLE));
        assertEquals(context.getString(R.string.app_name), notice.tickerText.toString());
    }
}
