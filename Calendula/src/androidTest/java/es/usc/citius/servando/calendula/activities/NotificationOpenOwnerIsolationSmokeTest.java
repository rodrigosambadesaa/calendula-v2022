/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;
import es.usc.citius.servando.calendula.util.IntentParams;
import es.usc.citius.servando.calendula.util.PendingIntentFlags;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Disposable real SQLite + Android PendingIntent regression for notification
 * content taps. Do not run against a user's personal medication database.
 * The old patient/reminder IDs are modelled from a previously removed row;
 * only the replacement synthetic owner is persisted.
 */
@RunWith(AndroidJUnit4.class)
public class NotificationOpenOwnerIsolationSmokeTest {

    private static Intent open(Context context, Patient patient, long reminderId,
                               String instance, DateTime time) {
        Intent intent = new Intent(context, ConfirmActivity.class);
        intent.putExtra(IntentParams.EXTRA_ACTION, IntentParams.ACTION_ALARM_REMINDER);
        intent.putExtra(IntentParams.EXTRA_PATIENT_ID, patient.getId());
        intent.putExtra(IntentParams.EXTRA_REMINDER_ID, reminderId);
        intent.putExtra(IntentParams.EXTRA_DATETIME,
                time.toString(ISODateTimeFormat.dateTimeNoMillis()));
        return IntakeNotificationMgr.identifyNotificationAction(
                intent, "OPEN", reminderId, instance);
    }

    @Test
    public void staleContentTapCannotReadReplacementPatientDespiteMatchingNumericIds()
            throws Exception {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DateTime time = DateTime.now().plusHours(10).withMillisOfSecond(0);
        Patient replacement = new Patient();
        replacement.setName("Disposable replacement owner");
        replacement.setCode("ci-notification-open-" + System.nanoTime());
        EventReminder current = new EventReminder(time, EventType.MEDICATION_INTAKE);
        current.setNextTime(time);
        current.setPatient(replacement);
        PendingIntent staleToken = null;
        PendingIntent currentToken = null;
        try {
            assertTrue(DB.patients().create(replacement) == 1);
            assertTrue(DB.eventReminders().create(current) == 1);
            assertNotNull(current.getId());
            EventReminder stored = DB.eventReminders().findById(current.getId());
            Patient persistedOwner = DB.patients().findById(replacement.getId());
            assertNotNull(stored);
            assertNotNull(persistedOwner);

            // Previous patient's old content token has the SAME reused
            // reminder ID, patient ID and displayed intake timestamp, but
            // a different immutable UUID from the replacement row.
            String obsoleteInstance = UUID.randomUUID().toString();
            Intent stale = open(context, persistedOwner,
                    current.getId(), obsoleteInstance, time);
            staleToken = PendingIntent.getActivity(context, current.getId().intValue(),
                    stale, PendingIntentFlags.immutable(PendingIntent.FLAG_UPDATE_CURRENT));
            assertNotNull(staleToken);
            ConfirmActivity.IntakeLaunch staleLaunch =
                    ConfirmActivity.parseIntakeLaunch(stale);
            assertNotNull(staleLaunch);
            assertFalse("A stale notification must never open successor clinical data",
                    ConfirmActivity.matchesCurrentNotificationIntake(
                            staleLaunch, stale, persistedOwner, stored));

            Intent valid = open(context, persistedOwner,
                    current.getId(), current.getAlarmInstance(), time);
            currentToken = PendingIntent.getActivity(context, current.getId().intValue(),
                    valid, PendingIntentFlags.immutable(PendingIntent.FLAG_UPDATE_CURRENT));
            assertNotNull(currentToken);
            assertFalse("Different UUIDs must not alias their PendingIntent",
                    staleToken.equals(currentToken));
            assertTrue("A matching current notification remains actionable",
                    ConfirmActivity.matchesCurrentNotificationIntake(
                            ConfirmActivity.parseIntakeLaunch(valid),
                            valid, persistedOwner, stored));
        } finally {
            if (staleToken != null) staleToken.cancel();
            if (currentToken != null) currentToken.cancel();
            if (current.getId() != null
                    && DB.eventReminders().findById(current.getId()) != null) {
                DB.eventReminders().remove(current);
            }
            if (replacement.getId() != null
                    && DB.patients().findById(replacement.getId()) != null) {
                DB.patients().remove(replacement);
            }
        }
    }
}
