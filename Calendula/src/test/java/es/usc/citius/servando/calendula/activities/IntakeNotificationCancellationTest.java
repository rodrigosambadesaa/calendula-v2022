/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import android.content.Context;

import androidx.core.app.NotificationManagerCompat;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.scheduling.model.EventReminder;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

/** No real medication records or Android notification IDs are used. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class IntakeNotificationCancellationTest {
    @Test
    public void missingOrUnpersistedReminderDoesNotTouchNotificationManager() {
        EventReminder unsaved = new EventReminder();
        // Passing a null context makes any accidental NotificationManager
        // lookup obvious; an unsaved reminder cannot have a notification ID.
        IntakeNotificationMgr.cancel(null, null);
        IntakeNotificationMgr.cancel(null, unsaved);
    }

    @Test
    public void persistedReminderStillCancelsItsOwnNotification() {
        Context context = ApplicationProvider.getApplicationContext();
        EventReminder saved = new EventReminder();
        saved.setId(9876543L);
        NotificationManagerCompat notifications = mock(NotificationManagerCompat.class);

        try (MockedStatic<NotificationManagerCompat> manager =
                     mockStatic(NotificationManagerCompat.class)) {
            manager.when(() -> NotificationManagerCompat.from(context))
                    .thenReturn(notifications);
            IntakeNotificationMgr.cancel(context, saved);
            verify(notifications).cancel("REMINDER-9876543", 9876543);
        }
    }
}
