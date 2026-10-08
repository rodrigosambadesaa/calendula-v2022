/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
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

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.drugdb.cima.CimaMedicineSearch;
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
import es.usc.citius.servando.calendula.util.IntentParams;
import es.usc.citius.servando.calendula.util.PendingIntentFlags;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
    public void medicationDatabaseSchemaIsReadableOnDevice() {
        // Read-only checks on the ephemeral emulator's own app database.
        // This covers first-run SQLite/ORM initialization, not migrations
        // from historical patient databases or download authenticity.
        assertTrue(DB.initialized);
        assertNotNull(DB.helper());
        assertTrue(DB.helper().getReadableDatabase().isOpen());
        assertTrue(DB.medicines().count() >= 0);
        assertTrue(DB.routines().count() >= 0);
        assertTrue(DB.schedules().count() >= 0);
        assertTrue(DB.eventReminders().count() >= 0);
    }

    @Test
    public void prescriptionSearchHandlesUntrustedCharactersAsData() {
        // Read-only query on disposable emulator data. Quotes, SQL comments,
        // and operators must never become executable ORDER BY syntax.
        assertNotNull(DB.drugDB().prescriptions()
                .findByNameOrCn("test\" OR 1=1 --", 5));
        assertNotNull(DB.drugDB().prescriptions()
                .findByNameOrCn("Ácido's 100% _", 5));
    }

    @Test
    public void officialCimaReadOnlySearchHandlesUnicodeOnDeviceWithoutNetwork() {
        // Offline integration probe on Android API 23/33/36. The real HTTPS
        // request and any persistence are deliberately excluded.
        String url = CimaMedicineSearch.url("Ácido ascórbico", 1);
        assertTrue(url.startsWith("https://cima.aemps.es/cima/rest/medicamentos?"));
        assertTrue(url.contains("%C3%81cido+asc%C3%B3rbico"));
        CimaMedicineSearch.Page page = CimaMedicineSearch.parsePage(
                "{\"totalFilas\":1,\"pagina\":1,"
                        + "\"resultados\":[{\"nregistro\":\"51347\","
                        + "\"nombre\":\"Ácido ascórbico\"}]}", 1);
        assertEquals(1, page.getResults().size());
        assertEquals("51347", page.getResults().get(0).getRegistrationNumber());
        assertEquals("Ácido ascórbico", page.getResults().get(0).getName());
    }

    @Test
    public void officialCimaRejectsCorruptUnicodeOnRealAndroidRuntime() {
        // No network access and no patient/database writes.
        try {
            CimaMedicineSearch.url("Med " + (char) 0xD800, 1);
            org.junit.Assert.fail("Unpaired UTF-16 must not reach the request encoder");
        } catch (IllegalArgumentException expected) {
            // Fail closed instead of silently replacing characters.
        }

        String invalidResponse = "{\"totalFilas\":1,\"pagina\":1,\"resultados\":["
                + "{\"nregistro\":\"123\",\"nombre\":\"Med "
                + (char) 0xDC00 + "\"}]}";
        try {
            CimaMedicineSearch.parsePage(invalidResponse, 1);
            org.junit.Assert.fail("Corrupted CIMA medicine name must not be accepted");
        } catch (IllegalArgumentException expected) {
            // No invalid medicine metadata is returned.
        }
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
    public void bootRestoresDailyAgendaAlarmOnDevice() {
        Context context = targetContext();
        AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        assertNotNull(alarmManager);
        Intent daily = new Intent(context, AlarmReceiver.class);
        daily.putExtra(IntentParams.EXTRA_ACTION, IntentParams.ACTION_DAILY_UPDATE);

        PendingIntent previous = PendingIntent.getBroadcast(context,
                IntentParams.DAILY_UPDATE_ID, daily,
                PendingIntentFlags.immutable(PendingIntent.FLAG_NO_CREATE));
        if (previous != null) {
            alarmManager.cancel(previous);
            previous.cancel();
        }
        assertNull("Test starts with no daily-update registration",
                PendingIntent.getBroadcast(context, IntentParams.DAILY_UPDATE_ID,
                        daily, PendingIntentFlags.immutable(PendingIntent.FLAG_NO_CREATE)));

        // A real boot discards app alarms. Explicitly invoke the receiver's
        // recovery path with synthetic emulator data and verify registration.
        new BootReceiver().onReceive(context, new Intent(Intent.ACTION_BOOT_COMPLETED));
        PendingIntent restored = PendingIntent.getBroadcast(context,
                IntentParams.DAILY_UPDATE_ID, daily,
                PendingIntentFlags.immutable(PendingIntent.FLAG_NO_CREATE));
        assertNotNull("Boot must restore daily schedule maintenance", restored);
        // Keep the restored daily update in place for the rest of the test app.
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
