/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.notifications;

import android.app.NotificationManager;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class NotificationHelperTest {

    @Test
    public void globallyBlockedNotificationsAreNotReportedAsAllowed() {
        Context context = ApplicationProvider.getApplicationContext();
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        shadowOf(manager).setNotificationsEnabled(false);

        assertFalse(NotificationHelper.canPostNotifications(context));
    }

    @Test
    public void systemEnabledNotificationsAreAllowedBeforeAndroid13() {
        Context context = ApplicationProvider.getApplicationContext();
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        shadowOf(manager).setNotificationsEnabled(true);

        assertTrue(NotificationHelper.canPostNotifications(context));
    }
}
