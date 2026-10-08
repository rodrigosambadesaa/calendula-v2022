/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationManagerCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import es.usc.citius.servando.calendula.notifications.NotificationHelper;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

/**
 * Reads the emulator's actual notification settings without mutating runtime
 * permissions or any patient data (permission revocation could kill the test process).
 */
@RunWith(AndroidJUnit4.class)
public class NotificationPermissionSmokeTest {

    @Test
    public void notificationGateReflectsDeviceSettingsAndRuntimePermission() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        boolean systemEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled();
        boolean runtimeGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        assertEquals(systemEnabled && runtimeGranted,
                NotificationHelper.canPostNotifications(context));
    }
}
