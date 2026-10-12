/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import android.app.PendingIntent;
import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.ScheduleUtils;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;
import es.usc.citius.servando.calendula.util.PendingIntentFlags;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Real Android reminder delay result contract for interactive confirmation:
 * success means SQL and OS reminder were actually updated. No patient data.
 */
@RunWith(AndroidJUnit4.class)
public class ConfirmScreenDelayResultSmokeTest {

    @Test
    public void noReminderOrInvalidMinutesMustNeverReportSuccessfulPostponement() {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Patient patient = new Patient();
        patient.setCode("ci-delay-missing-" + System.nanoTime());
        patient.setName("Synthetic no-reminder patient");
        DateTime when = DateTime.now().plusHours(7).withMillisOfSecond(0);
        try {
            DB.patients().save(patient);
            assertNotNull(patient.getId());
            assertNull(DB.eventReminders().findBy(EventType.MEDICATION_INTAKE, when, patient));
            assertFalse("No persisted reminder cannot be postponed",
                    ScheduleUtils.instance().delayIntake(context, patient, when, 5));
            assertFalse("Zero delay is not a clinical action",
                    ScheduleUtils.instance().delayIntake(context, patient, when, 0));
            assertFalse("Negative delay must not move a reminder into the past",
                    ScheduleUtils.instance().delayIntake(context, patient, when, -4));
            assertFalse("Overflowing delay minutes must not wrap into seconds",
                    ScheduleUtils.instance().delayIntake(
                            context, patient, when, Integer.MAX_VALUE));
        } finally {
            if (patient.getId() != null
                    && DB.patients().findById(patient.getId()) != null) {
                DB.patients().remove(patient);
            }
        }
    }

    @Test
    public void successIsReportedOnlyAfterRealSqliteDelayAndAndroidAlarm() {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DateTime when = DateTime.now().plusMinutes(8).withMillisOfSecond(0);
        Patient patient = new Patient();
        patient.setCode("ci-delay-success-" + System.nanoTime());
        patient.setName("Synthetic delay patient");
        EventInstance pending = new EventInstance(when, EventType.MEDICATION_INTAKE);
        EventReminder reminder = new EventReminder(when, EventType.MEDICATION_INTAKE);
        reminder.setNextTime(when);
        try {
            DB.patients().save(patient);
            assertNotNull(patient.getId());
            pending.setPatient(patient);
            reminder.setPatient(patient);
            DB.eventInstances().save(pending);
            DB.eventReminders().save(reminder);
            assertNotNull(pending.getId());
            assertNotNull(reminder.getId());

            assertTrue("UI may acknowledge only a persisted, rearmed reminder",
                    ScheduleUtils.instance().delayIntake(context, patient, when, 2));
            EventReminder committed = DB.eventReminders().findById(reminder.getId());
            assertNotNull(committed);
            assertTrue("Delay is persisted in SQLite, not just an in-memory flag",
                    committed.getNextTime().isBefore(when));
            assertTrue(committed.getNextTime().isAfter(DateTime.now().plusMinutes(1)));
            assertNotNull("The committed reminder is registered with Android",
                    PendingIntent.getBroadcast(context, 0,
                            Agenda.reminderBroadcastIntent(context, reminder),
                            PendingIntentFlags.immutable(PendingIntent.FLAG_NO_CREATE)));
            assertFalse(DB.eventInstances().findById(pending.getId()).completed());
            assertFalse(DB.eventInstances().findById(pending.getId()).cancelled());

            // After deleting the row, a stale confirmation screen cannot
            // show a false success toast or recreate that reminder.
            DB.eventReminders().remove(committed);
            assertFalse(ScheduleUtils.instance().delayIntake(context, patient, when, 2));
            assertNull(DB.eventReminders().findById(reminder.getId()));
        } finally {
            if (reminder.getId() != null) {
                Agenda.instance().cancelAlarm(context, reminder);
                if (DB.eventReminders().findById(reminder.getId()) != null) {
                    DB.eventReminders().remove(reminder);
                }
            }
            if (pending.getId() != null
                    && DB.eventInstances().findById(pending.getId()) != null) {
                DB.eventInstances().remove(pending);
            }
            if (patient.getId() != null
                    && DB.patients().findById(patient.getId()) != null) {
                DB.patients().remove(patient);
            }
        }
    }
}
