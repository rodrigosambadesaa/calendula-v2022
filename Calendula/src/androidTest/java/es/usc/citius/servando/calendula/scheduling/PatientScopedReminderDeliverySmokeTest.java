/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
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
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;
import es.usc.citius.servando.calendula.util.PendingIntentFlags;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * On-device SQLite/AlarmManager test with disposable synthetic patients only.
 * A patient's orphan reminder must never be delivered because another
 * patient has an event at the same time.
 */
@RunWith(AndroidJUnit4.class)
public class PatientScopedReminderDeliverySmokeTest {

    @Test
    public void cancelledEventCannotAuthorizeMedicationNotification() {
        exerciseInactiveEvent(false);
    }

    @Test
    public void completedEventCannotAuthorizeMedicationNotification() {
        exerciseInactiveEvent(true);
    }

    private void exerciseInactiveEvent(boolean completed) {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DateTime time = DateTime.now().plusHours(completed ? 17 : 18).withMillisOfSecond(0);
        Patient synthetic = new Patient();
        synthetic.setCode("ci-inactive-" + System.nanoTime());
        synthetic.setName("Synthetic inactive-event patient");
        EventInstance inactive = new EventInstance(time, EventType.MEDICATION_INTAKE);
        inactive.setPatient(synthetic);
        if (completed) {
            inactive.setCompleted(true);
        } else {
            inactive.setCancelled(true);
        }
        EventReminder reminder = new EventReminder(time, EventType.MEDICATION_INTAKE);
        reminder.setPatient(synthetic);
        reminder.setNextTime(time);
        try {
            DB.patients().save(synthetic);
            DB.eventInstances().save(inactive);
            DB.eventReminders().save(reminder);
            assertNotNull(inactive.getId());
            assertNotNull(reminder.getId());
            assertFalse("An inactive event must not authorize a reminder",
                    DB.eventInstances().existsPending(EventType.MEDICATION_INTAKE,
                            time, synthetic));
            Agenda.instance().setAlarm(context, reminder);
            assertNotNull(PendingIntent.getBroadcast(context, 0,
                    Agenda.reminderBroadcastIntent(context, reminder),
                    PendingIntentFlags.immutable(PendingIntent.FLAG_NO_CREATE)));

            // Should delete the orphan token without delivering medication
            // data for an already cancelled or completed intake.
            Agenda.instance().onReceiveAlarm(context, reminder.getId());

            assertNull(DB.eventReminders().findById(reminder.getId()));
            assertNull(PendingIntent.getBroadcast(context, 0,
                    Agenda.reminderBroadcastIntent(context, reminder),
                    PendingIntentFlags.immutable(PendingIntent.FLAG_NO_CREATE)));
            EventInstance untouched = DB.eventInstances().findById(inactive.getId());
            assertNotNull(untouched);
            if (completed) {
                assertTrue(untouched.completed());
            } else {
                assertTrue(untouched.cancelled());
            }
        } finally {
            if (reminder.getId() != null) {
                Agenda.instance().cancelAlarm(context, reminder);
                if (DB.eventReminders().findById(reminder.getId()) != null) {
                    DB.eventReminders().remove(reminder);
                }
            }
            if (inactive.getId() != null && DB.eventInstances().findById(inactive.getId()) != null) {
                DB.eventInstances().remove(inactive);
            }
            if (synthetic.getId() != null) {
                DB.patients().remove(synthetic);
            }
        }
    }

    @Test
    public void legacyReminderWithoutRegisteredHandlerDoesNotCrashOrChangeRecords() {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DateTime time = DateTime.now().minusMinutes(10).withMillisOfSecond(0);
        Patient patient = new Patient();
        patient.setCode("ci-no-handler-" + System.nanoTime());
        patient.setName("Synthetic unavailable receiver patient");
        // The base application registers a receiver only for medication
        // intakes. A legacy STOCK_REMINDER must not be dispatched through a
        // null receiver or falsely reported as delivered.
        EventInstance event = new EventInstance(time, EventType.STOCK_REMINDER);
        event.setPatient(patient);
        EventReminder reminder = new EventReminder(time, EventType.STOCK_REMINDER);
        reminder.setPatient(patient);
        reminder.setNextTime(time);
        try {
            DB.patients().save(patient);
            DB.eventInstances().save(event);
            DB.eventReminders().save(reminder);
            assertNotNull(event.getId());
            assertNotNull(reminder.getId());
            assertTrue(DB.eventInstances().existsPending(
                    EventType.STOCK_REMINDER, time, patient));

            Agenda.instance().onReceiveAlarm(context, reminder.getId());

            assertNotNull("Unknown receiver type must preserve the SQL reminder",
                    DB.eventReminders().findById(reminder.getId()));
            assertNotNull("Unknown receiver type must preserve its event",
                    DB.eventInstances().findById(event.getId()));
            assertFalse("The synthetic event must never be marked completed",
                    DB.eventInstances().findById(event.getId()).completed());
        } finally {
            if (reminder.getId() != null
                    && DB.eventReminders().findById(reminder.getId()) != null) {
                DB.eventReminders().remove(reminder);
            }
            if (event.getId() != null
                    && DB.eventInstances().findById(event.getId()) != null) {
                DB.eventInstances().remove(event);
            }
            if (patient.getId() != null) {
                DB.patients().remove(patient);
            }
        }
    }

    @Test
    public void eventForOtherPatientDoesNotAuthorizeOrphanMedicationReminder() {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DateTime time = DateTime.now().plusHours(13).withMillisOfSecond(0);
        Patient patientA = new Patient();
        patientA.setCode("ci-reminder-a-" + System.nanoTime());
        patientA.setName("Synthetic patient A");
        Patient patientB = new Patient();
        patientB.setCode("ci-reminder-b-" + System.nanoTime());
        patientB.setName("Synthetic patient B");

        EventInstance otherPatientEvent = new EventInstance(time, EventType.MEDICATION_INTAKE);
        otherPatientEvent.setPatient(patientB);
        EventReminder orphanForA = new EventReminder(time, EventType.MEDICATION_INTAKE);
        orphanForA.setPatient(patientA);
        orphanForA.setNextTime(time);

        try {
            DB.patients().save(patientA);
            DB.patients().save(patientB);
            DB.eventInstances().save(otherPatientEvent);
            DB.eventReminders().save(orphanForA);
            assertNotNull(otherPatientEvent.getId());
            assertNotNull(orphanForA.getId());

            assertFalse("Patient A must have no pending intake event",
                    DB.eventInstances().exists(EventType.MEDICATION_INTAKE,
                            time, patientA, false));
            assertTrue("Patient B must have a pending intake event",
                    DB.eventInstances().exists(EventType.MEDICATION_INTAKE,
                            time, patientB, false));

            Agenda.instance().setAlarm(context, orphanForA);
            PendingIntent registered = PendingIntent.getBroadcast(context, 0,
                    Agenda.reminderBroadcastIntent(context, orphanForA),
                    PendingIntentFlags.immutable(PendingIntent.FLAG_NO_CREATE));
            assertNotNull(registered);

            // Exercise the same handler used after receiving a real alarm,
            // without dispatching a medication notification or using real data.
            Agenda.instance().onReceiveAlarm(context, orphanForA.getId());

            assertNull("Orphan reminder for A must be removed",
                    DB.eventReminders().findById(orphanForA.getId()));
            assertNull("Orphan alarm token for A must be retired",
                    PendingIntent.getBroadcast(context, 0,
                            Agenda.reminderBroadcastIntent(context, orphanForA),
                            PendingIntentFlags.immutable(PendingIntent.FLAG_NO_CREATE)));
            assertNotNull("Another patient's event must remain untouched",
                    DB.eventInstances().findById(otherPatientEvent.getId()));
        } finally {
            if (orphanForA.getId() != null) {
                Agenda.instance().cancelAlarm(context, orphanForA);
                if (DB.eventReminders().findById(orphanForA.getId()) != null) {
                    DB.eventReminders().remove(orphanForA);
                }
            }
            if (otherPatientEvent.getId() != null
                    && DB.eventInstances().findById(otherPatientEvent.getId()) != null) {
                DB.eventInstances().remove(otherPatientEvent);
            }
            if (patientA.getId() != null) {
                DB.patients().remove(patientA);
            }
            if (patientB.getId() != null) {
                DB.patients().remove(patientB);
            }
        }
    }
}
