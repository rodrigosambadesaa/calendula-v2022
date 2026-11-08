/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import android.app.PendingIntent;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests real OS PendingIntent cancellation against disposable synthetic SQLite
 * data. Never run on a device containing actual patient records or alarms.
 */
@RunWith(AndroidJUnit4.class)
public class PatientErasureAlarmAtomicitySmokeTest {
    private static PendingIntent currentToken(Context ctx, EventReminder reminder) {
        return PendingIntent.getBroadcast(ctx, 0,
                Agenda.reminderBroadcastIntent(ctx, reminder),
                PendingIntentFlags.immutable(PendingIntent.FLAG_NO_CREATE));
    }

    @Test
    public void failedFinalSqlDeleteRetainsAlarmThenCommittedEraseCancelsIt()
            throws Exception {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SQLiteDatabase sqlite = DB.helper().getWritableDatabase();
        final String trigger = "ci_patient_alarm_rollback_abort";
        Patient patient = new Patient();
        patient.setName("Synthetic alarm rollback patient");
        patient.setCode("ci-alarm-erasure-" + System.nanoTime());
        DateTime when = DateTime.now().plusHours(7).withMillisOfSecond(0);
        EventReminder reminder = new EventReminder(when, EventType.MEDICATION_INTAKE);
        reminder.setNextTime(when);
        reminder.setPatient(patient);

        try {
            assertEquals(1, DB.patients().create(patient));
            assertEquals(1, DB.eventReminders().create(reminder));
            assertNotNull(reminder.getId());
            Agenda.instance().setAlarm(context, reminder);
            assertNotNull("Synthetic alarm must be registered", currentToken(context, reminder));

            sqlite.execSQL("CREATE TRIGGER " + trigger
                    + " BEFORE DELETE ON Patients WHEN OLD._id = " + patient.getId()
                    + " BEGIN SELECT RAISE(ABORT, 'ci-only patient erasure failure'); END;");

            boolean aborted = false;
            try {
                DB.patients().removeCascade(patient);
            } catch (RuntimeException expected) {
                aborted = true;
            }
            assertTrue("Late SQL failure must reject erasure", aborted);
            assertNotNull(DB.patients().findById(patient.getId()));
            assertNotNull("SQL reminder was rolled back",
                    DB.eventReminders().findById(reminder.getId()));
            assertNotNull("OS token must not be cancelled ahead of rollback",
                    currentToken(context, reminder));

            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            DB.patients().removeCascade(patient);
            assertNull(DB.patients().findById(patient.getId()));
            assertNull(DB.eventReminders().findById(reminder.getId()));
            assertNull("OS token cancelled only after successful commit",
                    currentToken(context, reminder));
        } finally {
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            Agenda.instance().cancelAlarm(context, reminder);
            if (reminder.getId() != null
                    && DB.eventReminders().findById(reminder.getId()) != null) {
                DB.eventReminders().remove(reminder);
            }
            if (patient.getId() != null
                    && DB.patients().findById(patient.getId()) != null) {
                DB.patients().remove(patient);
            }
        }
    }
}
