/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.security.NetworkSecurityPolicy;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import es.usc.citius.servando.calendula.drugdb.download.InstallDatabaseService;
import es.usc.citius.servando.calendula.drugdb.download.UpdateDatabaseService;
import es.usc.citius.servando.calendula.notifications.NotificationHelper;
import es.usc.citius.servando.calendula.scheduling.AlarmIntentService;
import es.usc.citius.servando.calendula.scheduling.AlarmReceiver;
import es.usc.citius.servando.calendula.scheduling.Agenda;
import es.usc.citius.servando.calendula.scheduling.BootReceiver;
import es.usc.citius.servando.calendula.scheduling.PickupAlarmReceiver;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;
import es.usc.citius.servando.calendula.util.NetworkUtils;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests real Android platform integration without accessing patient data or
 * contacting the legacy medication database server.
 */
@RunWith(AndroidJUnit4.class)
public class InstallationSmokeTest {

    private static Context targetContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private static boolean contains(String[] values, String expected) {
        if (values == null) {
            return false;
        }
        for (String value : values) {
            if (expected.equals(value)) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void instrumentationCanAccessInstalledApp() {
        Context context = targetContext();
        assertNotNull(context);
        assertEquals(BuildConfig.APPLICATION_ID, context.getPackageName());
        assertNotNull(context.getApplicationInfo());
        assertNotNull(context.getPackageManager());
    }

    @Test
    public void appDisablesSystemBackupOfMedicalData() {
        ApplicationInfo info = targetContext().getApplicationInfo();
        assertEquals(0, info.flags & ApplicationInfo.FLAG_ALLOW_BACKUP);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void alarmAndDatabaseComponentsAreNotExported() throws Exception {
        Context context = targetContext();
        PackageManager pm = context.getPackageManager();

        assertFalse(pm.getReceiverInfo(
                new ComponentName(context, AlarmReceiver.class), 0).exported);
        assertFalse(pm.getReceiverInfo(
                new ComponentName(context, PickupAlarmReceiver.class), 0).exported);
        assertFalse(pm.getReceiverInfo(
                new ComponentName(context, BootReceiver.class), 0).exported);

        assertFalse(pm.getServiceInfo(
                new ComponentName(context, InstallDatabaseService.class), 0).exported);
        assertFalse(pm.getServiceInfo(
                new ComponentName(context, UpdateDatabaseService.class), 0).exported);
        assertFalse(pm.getServiceInfo(
                new ComponentName(context, AlarmIntentService.class), 0).exported);
    }

    @Test
    public void bootReceiverRescheduleActionsDoNotCrashOnDevice() {
        Context context = targetContext();
        BootReceiver receiver = new BootReceiver();

        receiver.onReceive(context, new Intent(Intent.ACTION_BOOT_COMPLETED));
        receiver.onReceive(context, new Intent(Intent.ACTION_MY_PACKAGE_REPLACED));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            receiver.onReceive(
                    context,
                    new Intent(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED));
        }
    }

    @Test
    public void futureReminderCanBeScheduledOnDevice() {
        DateTime when = DateTime.now().plusHours(1);
        EventReminder reminder = new EventReminder(when, EventType.MEDICATION_INTAKE);
        reminder.setId(987654321L);
        reminder.setNextTime(when);

        Agenda.instance().setAlarm(targetContext(), reminder);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void alarmAndNotificationPermissionsAreDeclared() throws Exception {
        Context context = targetContext();
        PackageInfo info = context.getPackageManager().getPackageInfo(
                context.getPackageName(), PackageManager.GET_PERMISSIONS);

        assertTrue(contains(info.requestedPermissions, Manifest.permission.RECEIVE_BOOT_COMPLETED));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            assertTrue(contains(info.requestedPermissions, Manifest.permission.SCHEDULE_EXACT_ALARM));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            assertTrue(contains(info.requestedPermissions, Manifest.permission.POST_NOTIFICATIONS));
        }
    }

    @Test
    public void expectedNotificationChannelsAreAvailable() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return; // Channels did not exist prior to Android 8.0.
        }
        Context context = targetContext();
        NotificationHelper.createNotificationChannels(context);
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        assertNotNull(manager);

        NotificationChannel medicines =
                manager.getNotificationChannel(NotificationHelper.CHANNEL_MEDS_ID);
        assertNotNull(medicines);
        assertEquals(NotificationManager.IMPORTANCE_HIGH, medicines.getImportance());
        assertNotNull(manager.getNotificationChannel(NotificationHelper.CHANNEL_DEFAULT_ID));
        assertNotNull(manager.getNotificationChannel(NotificationHelper.CHANNEL_SETUP_ID));
    }

    @Test
    public void onlyTheLegacyDatabaseDomainCanUseCleartext() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return; // Host-specific network security policies are not supported.
        }
        NetworkSecurityPolicy policy = NetworkSecurityPolicy.getInstance();
        assertTrue(policy.isCleartextTrafficPermitted("tec.citius.usc.es"));
        assertFalse(policy.isCleartextTrafficPermitted("example.org"));
    }

    @Test
    public void connectivityStateIsReadableOnDevice() {
        assertNotNull(NetworkUtils.latestState(targetContext()));
    }
}
