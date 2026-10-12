/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import android.content.Intent;

import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.util.IntentParams;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Old/invalid alarm Intents are synthetic and never write patient data. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ConfirmIntentSafetyTest {

    @Test
    public void acceptsOnlyValidIsoMedicationReminderDates() {
        DateTime now = DateTime.now().withMillisOfSecond(0);
        String encoded = now.toString(ISODateTimeFormat.dateTimeNoMillis());
        DateTime parsed = ConfirmActivity.parseIntakeDate(encoded);
        assertNotNull(parsed);
        assertTrue(parsed.isEqual(now));
    }

    @Test
    public void missingOrMalformedAlarmTimestampNeverCrashesActivityParser() {
        assertNull(ConfirmActivity.parseIntakeDate(null));
        assertNull(ConfirmActivity.parseIntakeDate(""));
        assertNull(ConfirmActivity.parseIntakeDate(" "));
        assertNull(ConfirmActivity.parseIntakeDate("invalid-timestamp"));
        assertNull(ConfirmActivity.parseIntakeDate("2026-02-30T09:00:00+01:00"));
    }

    private static Intent validLaunch() {
        DateTime time = DateTime.now().withMillisOfSecond(0);
        return new Intent()
                .putExtra(IntentParams.EXTRA_PATIENT_ID, 800190L)
                .putExtra(IntentParams.EXTRA_DATETIME,
                        time.toString(ISODateTimeFormat.dateTimeNoMillis()))
                .putExtra(IntentParams.EXTRA_ACTION, IntentParams.ACTION_ALARM_REMINDER);
    }

    @Test
    public void correctHomeAndNotificationIntentsAreParsedWithoutMutation() {
        Intent notification = validLaunch();
        ConfirmActivity.IntakeLaunch fromAlarm =
                ConfirmActivity.parseIntakeLaunch(notification);
        assertNotNull(fromAlarm);
        assertEquals(800190L, fromAlarm.patientId);
        assertEquals(-1, fromAlarm.position);
        assertNotNull(fromAlarm.time);
        assertEquals(IntentParams.ACTION_ALARM_REMINDER, fromAlarm.action);
        notification.putExtra(IntentParams.EXTRA_POSITION, 3);
        ConfirmActivity.IntakeLaunch fromAgenda =
                ConfirmActivity.parseIntakeLaunch(notification);
        assertNotNull(fromAgenda);
        assertEquals(3, fromAgenda.position);
    }

    @Test
    public void malformedAndWrongTypedFieldsCannotLaunchMedicationConfirmation() {
        assertNull(ConfirmActivity.parseIntakeLaunch(null));
        assertNull(ConfirmActivity.parseIntakeLaunch(new Intent()));
        Intent valid = validLaunch();
        assertNotNull(ConfirmActivity.parseIntakeLaunch(valid));

        valid.putExtra(IntentParams.EXTRA_PATIENT_ID, "800190");
        assertNull("String is not a persisted Long patient ID",
                ConfirmActivity.parseIntakeLaunch(valid));
        valid.putExtra(IntentParams.EXTRA_PATIENT_ID, 800190);
        assertNull("Integer extra must not impersonate an exact Long patient ID",
                ConfirmActivity.parseIntakeLaunch(valid));
        valid.putExtra(IntentParams.EXTRA_PATIENT_ID, 800190L);
        valid.putExtra(IntentParams.EXTRA_DATETIME, 1234567L);
        assertNull("Long cannot impersonate an ISO-8601 intake date",
                ConfirmActivity.parseIntakeLaunch(valid));
        valid.putExtra(IntentParams.EXTRA_DATETIME, "bad date");
        assertNull(ConfirmActivity.parseIntakeLaunch(valid));
        valid.putExtra(IntentParams.EXTRA_DATETIME, DateTime.now().withMillisOfSecond(0)
                .toString(ISODateTimeFormat.dateTimeNoMillis()));
        valid.putExtra(IntentParams.EXTRA_POSITION, "3");
        assertNull("Wrong-typed list position cannot be converted to notification mode",
                ConfirmActivity.parseIntakeLaunch(valid));
        valid.putExtra(IntentParams.EXTRA_POSITION, -2);
        assertNull(ConfirmActivity.parseIntakeLaunch(valid));
        valid.putExtra(IntentParams.EXTRA_POSITION, 0);
        valid.putExtra(IntentParams.EXTRA_ACTION, 1);
        assertNull("Unexpected action object cannot reach the clinical screen",
                ConfirmActivity.parseIntakeLaunch(valid));
    }

    @Test
    public void patientMustBeSavedAndPresentBeforeAnyConfirmationUi() {
        DateTime time = DateTime.now();
        assertFalse(ConfirmActivity.hasValidIntakeContext(null, time));
        assertFalse(ConfirmActivity.hasValidIntakeContext(new Patient(), time));
        Patient patient = new Patient();
        patient.setId(0L);
        assertFalse(ConfirmActivity.hasValidIntakeContext(patient, time));
        patient.setId(800190L);
        assertFalse(ConfirmActivity.hasValidIntakeContext(patient, null));
        assertTrue(ConfirmActivity.hasValidIntakeContext(patient, time));
    }
}
