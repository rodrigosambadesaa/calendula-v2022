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
