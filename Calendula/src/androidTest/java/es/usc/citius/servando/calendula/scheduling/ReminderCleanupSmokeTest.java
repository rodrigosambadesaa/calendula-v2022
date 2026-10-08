/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
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
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;
import es.usc.citius.servando.calendula.util.PendingIntentFlags;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Real OS AlarmManager/SQLite smoke on the disposable emulator app.
 * Synthetic reminder only; no patient, prescription or live database records.
 */
@RunWith(AndroidJUnit4.class)
public class ReminderCleanupSmokeTest {

    private PendingIntent existing(Context context, EventReminder reminder) {
        return PendingIntent.getBroadcast(context, 0,
                Agenda.reminderBroadcastIntent(context, reminder),
                PendingIntentFlags.immutable(PendingIntent.FLAG_NO_CREATE));
    }

    @Test
    public void cleanupRemovesPersistedSyntheticReminderAndItsAlarm() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue("Emulator test app must have initialized its database", DB.initialized);

        DateTime future = DateTime.now().plusHours(4).withMillisOfSecond(0);
        // Exercise the real ORM foreign-key query with a synthetic persisted
        // patient. Passing null bypasses the production patient linkage and
        // can generate an invalid ORMLite foreign-key query.
        Patient synthetic = new Patient();
        synthetic.setCode("ci-cleanup-" + System.nanoTime());
        synthetic.setName("Synthetic emulator fixture");
        EventReminder reminder = new EventReminder(future, EventType.MEDICATION_INTAKE);
        reminder.setNextTime(future);
        try {
            DB.patients().save(synthetic);
            assertNotNull("Synthetic patient must be persisted", synthetic.getId());
            reminder.setPatient(synthetic);
            DB.eventReminders().save(reminder);
            assertNotNull("Persisted synthetic reminder must have an ID", reminder.getId());
            assertNotNull(DB.eventReminders().findById(reminder.getId()));

            Agenda.instance().setAlarm(context, reminder);
            assertNotNull("Synthetic OS alarm must be registered",
                    existing(context, reminder));

            Agenda.instance().cleanReminderIfPossible(
                    context, synthetic, EventType.MEDICATION_INTAKE, future);

            assertNull("Cleanup must delete the synthetic reminder row",
                    DB.eventReminders().findById(reminder.getId()));
            assertNull("Cleanup must revoke its scheduled Android broadcast",
                    existing(context, reminder));
        } finally {
            // Exception-safe fixture cleanup. Never delete other test app rows.
            if (reminder.getId() != null) {
                Agenda.instance().cancelAlarm(context, reminder);
                if (DB.eventReminders().findById(reminder.getId()) != null) {
                    DB.eventReminders().remove(reminder);
                }
            }
            if (synthetic.getId() != null) {
                DB.patients().remove(synthetic);
            }
        }
    }
}
