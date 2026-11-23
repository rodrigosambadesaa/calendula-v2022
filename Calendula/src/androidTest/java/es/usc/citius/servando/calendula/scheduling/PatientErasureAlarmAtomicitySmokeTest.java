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

import java.util.Collections;

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


    /**
     * A post-COMMIT alarm cleanup may be delayed while another patient
     * acquires the same SQLite reminder row number. A stale deletion snapshot
     * is never permission to cancel the replacement patient's OS token.
     */
    @Test
    public void staleDeletionSnapshotCannotCancelReusedReminderIdAlarm()
            throws Exception {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String nonce = Long.toString(System.nanoTime());
        Patient retiredOwner = new Patient();
        retiredOwner.setName("Synthetic retired reminder owner");
        retiredOwner.setCode("ci-retired-alarm-owner-" + nonce);
        Patient currentOwner = new Patient();
        currentOwner.setName("Synthetic current reminder owner");
        currentOwner.setCode("ci-current-alarm-owner-" + nonce);
        DateTime when = DateTime.now().plusHours(8).withMillisOfSecond(0);
        EventReminder current = new EventReminder(when, EventType.MEDICATION_INTAKE);
        current.setPatient(currentOwner);
        current.setNextTime(when);

        try {
            assertEquals(1, DB.patients().create(retiredOwner));
            assertEquals(1, DB.patients().create(currentOwner));
            assertEquals(1, DB.eventReminders().create(current));
            assertNotNull(current.getId());
            Agenda.instance().setAlarm(context, current);
            assertNotNull("Current patient's synthetic alarm must exist",
                    currentToken(context, current));

            // Emulate a delayed cleanup snapshot carrying the same SQL row ID
            // from an erased different owner. The current row and OS token
            // belong only to currentOwner; no real patient data is involved.
            EventReminder obsolete = new EventReminder(when, EventType.MEDICATION_INTAKE);
            obsolete.setId(current.getId());
            obsolete.setPatient(retiredOwner);
            obsolete.setNextTime(when);
            PendingIntent retiredToken = PendingIntent.getBroadcast(context, 0,
                    Agenda.reminderBroadcastIntent(context, obsolete),
                    PendingIntentFlags.immutable(PendingIntent.FLAG_UPDATE_CURRENT));
            try {
                assertNotNull("Retired owner must have an old OS token", retiredToken);
                Agenda.instance().cancelDeletedPatientReminderTokens(
                        context, Collections.singletonList(obsolete));
                assertNull("Old patient's captured token must be cancelled",
                        currentToken(context, obsolete));
                assertNotNull("Reused reminder ID must not cancel another owner's alarm",
                        currentToken(context, current));
            } finally {
                retiredToken.cancel();
            }
            EventReminder persisted = DB.eventReminders().findById(current.getId());
            assertNotNull("Replacement reminder must survive old cleanup", persisted);
            assertEquals(currentOwner.getId(), persisted.getPatient().getId());
            assertNotNull(DB.patients().findById(retiredOwner.getId()));
        } finally {
            if (current.getId() != null) {
                Agenda.instance().cancelAlarm(context, current);
                if (DB.eventReminders().findById(current.getId()) != null) {
                    DB.eventReminders().remove(current);
                }
            }
            for (Patient owner : new Patient[]{retiredOwner, currentOwner}) {
                if (owner.getId() != null && DB.patients().findById(owner.getId()) != null) {
                    DB.patients().remove(owner);
                }
            }
        }
    }


    /**
     * Simulate process termination after SQLite COMMIT but before OS cleanup.
     * The persisted reminder has been deleted, yet AlarmManager still holds
     * its token. Repeating post-COMMIT cleanup must be safe and idempotent.
     */
    @Test
    public void postCommitAlarmCancellationCanRetryAfterProcessInterruption()
            throws Exception {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Patient owner = new Patient();
        owner.setCode("ci-post-commit-alarm-" + System.nanoTime());
        owner.setName("Synthetic interrupted erasure owner");
        DateTime when = DateTime.now().plusHours(9).withMillisOfSecond(0);
        EventReminder removedSnapshot = new EventReminder(when, EventType.MEDICATION_INTAKE);
        removedSnapshot.setNextTime(when);
        removedSnapshot.setPatient(owner);

        try {
            assertEquals(1, DB.patients().create(owner));
            assertEquals(1, DB.eventReminders().create(removedSnapshot));
            assertNotNull(removedSnapshot.getId());
            Agenda.instance().setAlarm(context, removedSnapshot);
            assertNotNull("Synthetic alarm was registered",
                    currentToken(context, removedSnapshot));

            // Simulate the already-committed SQL-only deletion and a process
            // interruption before the platform PendingIntent can be retired.
            DB.helper().getWritableDatabase().execSQL(
                    "DELETE FROM EventReminders WHERE _id = ?",
                    new Object[]{removedSnapshot.getId()});
            assertNull(DB.eventReminders().findById(removedSnapshot.getId()));
            assertNotNull("A SQLite commit alone cannot cancel an OS token",
                    currentToken(context, removedSnapshot));

            Agenda.instance().cancelDeletedPatientReminderTokens(
                    context, Collections.singletonList(removedSnapshot));
            assertNull("Retry removes the stale committed alarm",
                    currentToken(context, removedSnapshot));
            Agenda.instance().cancelDeletedPatientReminderTokens(
                    context, Collections.singletonList(removedSnapshot));
            assertNull("Cleanup remains idempotent on a second retry",
                    currentToken(context, removedSnapshot));
            assertNotNull("Post-commit token cleanup never deletes a patient",
                    DB.patients().findById(owner.getId()));
        } finally {
            if (removedSnapshot.getId() != null) {
                Agenda.instance().cancelAlarm(context, removedSnapshot);
                if (DB.eventReminders().findById(removedSnapshot.getId()) != null) {
                    DB.eventReminders().deleteById(removedSnapshot.getId());
                }
            }
            if (owner.getId() != null && DB.patients().findById(owner.getId()) != null) {
                DB.patients().remove(owner);
            }
        }
    }


    /**
     * Exercise the real patient-erasure entry point with two disposable owners
     * and two actual PendingIntents. SQL deletion and OS cancellation must
     * affect only the requested patient, not the survivor's reminder.
     */
    @Test
    public void erasingOnePatientLeavesOtherPatientsAlarmUntouched()
            throws Exception {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String nonce = Long.toString(System.nanoTime());
        Patient erased = new Patient();
        erased.setName("Synthetic erased alarm owner");
        erased.setCode("ci-erased-alarm-owner-" + nonce);
        Patient survivor = new Patient();
        survivor.setName("Synthetic surviving alarm owner");
        survivor.setCode("ci-surviving-alarm-owner-" + nonce);
        DateTime when = DateTime.now().plusHours(10).withMillisOfSecond(0);
        EventReminder removedReminder =
                new EventReminder(when, EventType.MEDICATION_INTAKE);
        removedReminder.setNextTime(when);
        removedReminder.setPatient(erased);
        EventReminder survivingReminder =
                new EventReminder(when.plusMinutes(1), EventType.MEDICATION_INTAKE);
        survivingReminder.setNextTime(when.plusMinutes(1));
        survivingReminder.setPatient(survivor);

        try {
            assertEquals(1, DB.patients().create(erased));
            assertEquals(1, DB.patients().create(survivor));
            assertEquals(1, DB.eventReminders().create(removedReminder));
            assertEquals(1, DB.eventReminders().create(survivingReminder));
            assertNotNull(removedReminder.getId());
            assertNotNull(survivingReminder.getId());
            Agenda.instance().setAlarm(context, removedReminder);
            Agenda.instance().setAlarm(context, survivingReminder);
            assertNotNull("Erased owner's test alarm must exist",
                    currentToken(context, removedReminder));
            assertNotNull("Surviving owner's test alarm must exist",
                    currentToken(context, survivingReminder));

            DB.patients().removeCascade(erased);

            assertNull("Erased owner must be gone", DB.patients().findById(erased.getId()));
            assertNull("Erased reminder must be gone",
                    DB.eventReminders().findById(removedReminder.getId()));
            assertNull("Erased alarm must be cancelled only after commit",
                    currentToken(context, removedReminder));
            assertNotNull("Unrelated patient must survive",
                    DB.patients().findById(survivor.getId()));
            EventReminder remaining =
                    DB.eventReminders().findById(survivingReminder.getId());
            assertNotNull("Unrelated reminder must survive", remaining);
            assertEquals(survivor.getId(), remaining.getPatient().getId());
            assertNotNull("Unrelated OS alarm must remain scheduled",
                    currentToken(context, survivingReminder));
        } finally {
            for (EventReminder reminder :
                    new EventReminder[]{removedReminder, survivingReminder}) {
                if (reminder.getId() != null) {
                    Agenda.instance().cancelAlarm(context, reminder);
                    if (DB.eventReminders().findById(reminder.getId()) != null) {
                        DB.eventReminders().deleteById(reminder.getId());
                    }
                }
            }
            for (Patient owner : new Patient[]{erased, survivor}) {
                if (owner.getId() != null
                        && DB.patients().findById(owner.getId()) != null) {
                    DB.patients().remove(owner);
                }
            }
        }
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

    /**
     * A reminder-row DELETE failure occurs before the final patient DELETE.
     * It must roll back the entire patient erasure and leave AlarmManager alone.
     * This exercises the explicit checked reminder-delete path, not merely
     * the later patient-row failure covered by the separate regression.
     */
    @Test
    public void failedReminderDeleteRollsBackPatientAndKeepsAlarm() throws Exception {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SQLiteDatabase sqlite = DB.helper().getWritableDatabase();
        final String trigger = "ci_patient_reminder_rollback_abort";
        Patient patient = new Patient();
        patient.setName("Synthetic reminder-delete rollback patient");
        patient.setCode("ci-reminder-rollback-" + System.nanoTime());
        DateTime when = DateTime.now().plusHours(11).withMillisOfSecond(0);
        EventReminder reminder = new EventReminder(when, EventType.MEDICATION_INTAKE);
        reminder.setNextTime(when);
        reminder.setPatient(patient);

        try {
            assertEquals(1, DB.patients().create(patient));
            assertEquals(1, DB.eventReminders().create(reminder));
            assertNotNull(reminder.getId());
            Agenda.instance().setAlarm(context, reminder);
            assertNotNull("Test alarm must exist before erasure", currentToken(context, reminder));

            sqlite.execSQL("CREATE TRIGGER " + trigger
                    + " BEFORE DELETE ON EventReminders WHEN OLD._id = " + reminder.getId()
                    + " BEGIN SELECT RAISE(ABORT, 'ci-only reminder erasure failure'); END;");

            boolean failed = false;
            try {
                DB.patients().removeCascade(patient);
            } catch (RuntimeException expected) {
                failed = true;
            }
            assertTrue("Failed reminder DELETE must abort full erasure", failed);
            assertNotNull("Patient must survive transaction rollback",
                    DB.patients().findById(patient.getId()));
            assertNotNull("Reminder must survive transaction rollback",
                    DB.eventReminders().findById(reminder.getId()));
            assertNotNull("SQL rollback must not revoke Android alarm",
                    currentToken(context, reminder));

            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            DB.patients().removeCascade(patient);
            assertNull("Successful retry must delete patient",
                    DB.patients().findById(patient.getId()));
            assertNull("Successful retry must delete reminder",
                    DB.eventReminders().findById(reminder.getId()));
            assertNull("Successful retry must retire Android alarm",
                    currentToken(context, reminder));
        } finally {
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            if (reminder.getId() != null) {
                Agenda.instance().cancelAlarm(context, reminder);
                if (DB.eventReminders().findById(reminder.getId()) != null) {
                    DB.eventReminders().deleteById(reminder.getId());
                }
            }
            if (patient.getId() != null && DB.patients().findById(patient.getId()) != null) {
                DB.patients().remove(patient);
            }
        }
    }
}
