package es.usc.citius.servando.calendula.healthcareprovider.notifications;

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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mockStatic;
import org.mockito.MockedStatic;
import es.usc.citius.servando.calendula.util.PreferenceUtils;
import es.usc.citius.servando.calendula.util.PreferenceKeys;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ActiveMedNotificationPrivacyTest {
    @Test
    public void publicPreviewContainsOnlyGenericAppTitle() {
        Context app = RuntimeEnvironment.getApplication();
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(app, NotificationHelper.CHANNEL_MEDS_ID)
                        .setSmallIcon(R.drawable.ic_active_med_update_w)
                        .setContentTitle("private details");
        ActiveMedNotification.applyLockscreenPrivacy(app, builder);
        Notification notification = builder.build();
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility);
        assertNotNull(notification.publicVersion);
        assertEquals(app.getString(R.string.app_name),
                notification.publicVersion.extras.getString(Notification.EXTRA_TITLE));
        assertEquals(app.getString(R.string.app_name),
                notification.publicVersion.extras.getString(Notification.EXTRA_TEXT));
        assertEquals(app.getString(R.string.app_name),
                notification.tickerText.toString());
    }

    @Test
    public void wrongTypeSavedInsistentSwitchDefaultsSafely() {
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getBoolean(
                    PreferenceKeys.SETTINGS_ALARM_INSISTENT, false))
                    .thenThrow(new ClassCastException("legacy setting"));
            assertFalse(ActiveMedNotification.configuredInsistentNotifications());
        }
    }

    @Test
    public void validInsistentSwitchRemainsEnabled() {
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getBoolean(
                    PreferenceKeys.SETTINGS_ALARM_INSISTENT, false))
                    .thenReturn(true);
            assertTrue(ActiveMedNotification.configuredInsistentNotifications());
        }
    }
}
