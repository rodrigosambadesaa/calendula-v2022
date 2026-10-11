/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.persistence.Patient;

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
