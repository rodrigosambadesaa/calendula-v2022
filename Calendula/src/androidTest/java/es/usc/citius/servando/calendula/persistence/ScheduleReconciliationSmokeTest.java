/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.persistence;

import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.joda.time.DateTime;
import org.dmfs.rfc5545.recur.Freq;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;

import es.usc.citius.servando.calendula.scheduling.model.EventReminder;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventType;
import es.usc.citius.servando.calendula.util.alerts.ScheduleAlarmRecovery;
import es.usc.citius.servando.calendula.scheduling.model.recur.RecurringEvent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Real SQLite failure injection; synthetic references and past timestamps only. */
@RunWith(AndroidJUnit4.class)
public class ScheduleReconciliationSmokeTest {
    @Test
    public void newlyCreatedScheduleCannotClaimSuccessWhenReminderWriteFails() {
        assertTrue(DB.initialized);
        DateTime when = DateTime.now().plusMinutes(2).withMillisOfSecond(0);
        Long ref = -System.nanoTime();
        EventInstance pending = event(ref, when, 1.0d);
        SQLiteDatabase sqlite = DB.helper().getWritableDatabase();
        final String trigger = "ci_test_new_schedule_reminder_abort";
        sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
        try {
            sqlite.execSQL("CREATE TRIGGER " + trigger
                    + " BEFORE INSERT ON EventReminders"
                    + " BEGIN SELECT RAISE(ABORT, 'synthetic reminder insertion failure'); END;");
            boolean rejected = false;
            try {
                ScheduleUtils.instance().persistAndScheduleNewOccurrences(
                        InstrumentationRegistry.getInstrumentation().getTargetContext(),
                        Collections.singletonList(pending));
            } catch (RuntimeException expected) {
                rejected = true;
            }
            assertTrue("Failure must be reported, never disguised as saved", rejected);
            assertNotNull("Committed occurrence remains available for recovery",
                    pending.getId());
            assertNotNull(DB.eventInstances().findById(pending.getId()));
            assertNull("SQL failure must not leave half-created reminder",
                    DB.eventReminders().findBy(EventType.MEDICATION_INTAKE,
                            when, null));
        } finally {
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            EventReminder reminder = DB.eventReminders().findBy(
                    EventType.MEDICATION_INTAKE, when, null);
            // The injected BEFORE INSERT abort means no alarm could have
            // been registered. Defensive SQLite cleanup uses public DAO APIs.
            if (reminder != null) {
                DB.eventReminders().remove(reminder);
            }
            if (pending.getId() != null && DB.eventInstances().findById(pending.getId()) != null) {
                DB.eventInstances().remove(pending);
            }
        }
    }

    @Test
    public void newScheduleRejectsRecycledPatientMedicineAndInitialDoseOwners()
            throws Exception {
        assertTrue(DB.initialized);
        String suffix = Long.toString(System.nanoTime());
        Patient owner = new Patient();
        owner.setCode("ci-create-stable-schedule-owner-" + suffix);
        owner.setName("Synthetic prescription owner");
        Medicine medicine = new Medicine("Synthetic new prescription medicine",
                Presentation.PILLS);
        RecurringEvent recur = new RecurringEvent.Builder()
                .from(DateTime.now().minusDays(1))
                .repeatEvery(12, Freq.HOURLY).build();
        Schedule planned = new Schedule(recur);
        Schedule staleDoseSchedule = new Schedule(recur);
        try {
            assertEquals(1, DB.patients().create(owner));
            medicine.setPatient(owner);
            assertEquals(1, DB.medicines().create(medicine));

            Patient successor = new Patient();
            successor.setId(owner.getId());
            successor.setCode("ci-restored-other-owner-" + suffix);
            planned.setPatient(successor);
            planned.setMedicine(medicine);
            planned.setDosage(1.0d);
            try {
                ScheduleUtils.instance().persistNewScheduleRows(
                        InstrumentationRegistry.getInstrumentation().getTargetContext(),
                        planned, Collections.emptyList());
                fail("Reused patient ID must not create a prescription");
            } catch (RuntimeException expected) {
                // The original stable patient code is still required.
            }
            assertNull("Failed owner validation cannot allocate a schedule row",
                    planned.getId());
            assertTrue(DB.schedules().findByMedicine(medicine).isEmpty());

            planned.setPatient(owner);
            Medicine misleading = new Medicine("Synthetic replaced medicine",
                    Presentation.PILLS);
            misleading.setId(medicine.getId());
            misleading.setPatient(successor);
            planned.setMedicine(misleading);
            try {
                ScheduleUtils.instance().persistNewScheduleRows(
                        InstrumentationRegistry.getInstrumentation().getTargetContext(),
                        planned, Collections.emptyList());
                fail("Same numeric medicine ID must not authorize another owner");
            } catch (RuntimeException expected) {
                // Owner mismatch is rejected before assigning an SQL ID.
            }
            assertNull(planned.getId());
            assertTrue(DB.schedules().findByMedicine(medicine).isEmpty());

            // The schedule and medicine can both be genuine while a stale
            // precomputed intake still identifies a different restored owner.
            staleDoseSchedule.setPatient(owner);
            staleDoseSchedule.setMedicine(medicine);
            staleDoseSchedule.setDosage(1.0d);
            EventInstance foreignDose = new EventInstance(
                    DateTime.now().plusDays(1), EventType.MEDICATION_INTAKE);
            foreignDose.setPatient(successor);
            foreignDose.addParam(EventInstance.PARAM_DOSE, 1.0d);
            try {
                ScheduleUtils.instance().persistNewScheduleRows(
                        InstrumentationRegistry.getInstrumentation().getTargetContext(),
                        staleDoseSchedule, Collections.singletonList(foreignDose));
                fail("New occurrence with recycled patient ID must roll back schedule");
            } catch (RuntimeException expected) {
                // The SQL transaction must reject this occurrence.
            }
            // A failed SQLite INSERT must also restore the transient draft:
            // its stale generated ID must never be reused on retry.
            assertNull("Rejected initial occurrence must reset schedule ID",
                    staleDoseSchedule.getId());
            assertNull("Rejected initial occurrence must not have an ID",
                    foreignDose.getId());
            assertNull("Rejected occurrence must restore its original reference",
                    foreignDose.getRef());
            assertEquals("Rejected occurrence must keep its original owner",
                    successor, foreignDose.getPatient());
            assertTrue("No partially inserted schedule may remain",
                    DB.schedules().findByMedicine(medicine).isEmpty());

            // Control: original owner, original medicine and valid persisted
            // patient code can still create a real synthetic prescription.
            planned.setMedicine(medicine);
            ScheduleUtils.instance().persistNewScheduleRows(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(),
                    planned, Collections.emptyList());
            assertNotNull(planned.getId());
            assertEquals(owner.getCode(),
                    DB.schedules().findById(planned.getId()).getPatient().getCode());
        } finally {
            for (Schedule schedule : new Schedule[]{planned, staleDoseSchedule}) {
                if (schedule.getId() != null
                        && DB.schedules().findById(schedule.getId()) != null) {
                    DB.eventInstances().removeByRef(schedule.getId());
                    DB.schedules().remove(schedule);
                }
            }
            if (medicine.getId() != null
                    && DB.medicines().findById(medicine.getId()) != null) {
                DB.medicines().remove(medicine);
            }
            if (owner.getId() != null
                    && DB.patients().findById(owner.getId()) != null) {
                DB.patients().remove(owner);
            }
        }
    }

    @Test
    public void failedInitialDoseInsertRollsBackNewScheduleRow() throws Exception {
        assertTrue(DB.initialized);
        String key = Long.toString(System.nanoTime());
        Patient owner = new Patient();
        owner.setCode("ci-atomic-schedule-owner-" + key);
        owner.setName("Synthetic new schedule owner");
        Medicine medicine = new Medicine("Synthetic initial schedule medicine", Presentation.PILLS);
        Schedule schedule = new Schedule(medicine);
        DateTime when = DateTime.now().plusDays(3).withMillisOfSecond(0);
        EventInstance pending = new EventInstance(when, EventType.MEDICATION_INTAKE);
        pending.addParam(EventInstance.PARAM_DOSE, 1.0d);
        SQLiteDatabase sqlite = DB.helper().getWritableDatabase();
        final String trigger = "ci_new_schedule_event_abort";
        sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
        try {
            assertEquals(1, DB.patients().create(owner));
            medicine.setPatient(owner);
            assertEquals(1, DB.medicines().create(medicine));
            schedule.setPatient(owner);
            sqlite.execSQL("CREATE TRIGGER " + trigger
                    + " BEFORE INSERT ON EventInstances"
                    + " BEGIN SELECT RAISE(ABORT, 'synthetic event insert failure'); END;");
            boolean rejected = false;
            try {
                ScheduleUtils.instance().persistNewScheduleRows(
                        InstrumentationRegistry.getInstrumentation().getTargetContext(),
                        schedule, java.util.Collections.singletonList(pending));
            } catch (RuntimeException expected) {
                rejected = true;
            }
            assertTrue("A failed dose insert must fail prescription creation", rejected);
            assertNull("Failed schedule draft must not keep a phantom SQLite ID",
                    schedule.getId());
            assertNull("Failed dose draft must not keep a phantom SQLite ID",
                    pending.getId());
            assertNull("Rolled back dose must not point to an absent schedule",
                    pending.getRef());
            assertNull("Original unassigned synthetic event owner must survive rollback",
                    pending.getPatient());
            assertTrue("New prescription must be rolled back completely",
                    DB.schedules().findByMedicine(medicine).isEmpty());
            assertTrue("The failed intake must not be persisted",
                    DB.eventInstances().findByTypeAndTime(
                            EventType.MEDICATION_INTAKE, when).isEmpty());
            assertNotNull("The patient must not be deleted by rollback",
                    DB.patients().findById(owner.getId()));
            assertNotNull("The medicine must not be deleted by rollback",
                    DB.medicines().findById(medicine.getId()));

            // Reuse the SAME Java draft, not a new object. No phantom
            // generated ID may cause update() instead of a true INSERT.
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            ScheduleUtils.instance().persistNewScheduleRows(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(),
                    schedule, java.util.Collections.singletonList(pending));
            assertNotNull("Retry must commit a new prescription", schedule.getId());
            assertNotNull("Retry must commit its pending intake", pending.getId());
            assertEquals(schedule.getId(), pending.getRef());
            assertNotNull(DB.schedules().findById(schedule.getId()));
            assertNotNull(DB.eventInstances().findById(pending.getId()));
        } finally {
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            if (pending.getId() != null && DB.eventInstances().findById(pending.getId()) != null) {
                DB.eventInstances().remove(pending);
            }
            if (schedule.getId() != null && DB.schedules().findById(schedule.getId()) != null) {
                DB.schedules().remove(schedule);
            }
            if (medicine.getId() != null && DB.medicines().findById(medicine.getId()) != null) {
                DB.medicines().remove(medicine);
            }
            if (owner.getId() != null && DB.patients().findById(owner.getId()) != null) {
                DB.patients().remove(owner);
            }
        }
    }

    @Test
    public void failedSecondNewDoseInsertRestoresEarlierGeneratedIdsForRetry()
            throws Exception {
        assertTrue(DB.initialized);
        String key = Long.toString(System.nanoTime());
        Patient owner = new Patient();
        owner.setCode("ci-second-dose-rollback-owner-" + key);
        owner.setName("Synthetic second-dose rollback owner");
        Medicine medicine = new Medicine("Synthetic multi-dose medicine",
                Presentation.PILLS);
        Schedule schedule = new Schedule(medicine);
        DateTime firstTime = DateTime.now().plusDays(5).withMillisOfSecond(0);
        EventInstance first = event(null, firstTime, 1.0d);
        EventInstance second = event(null, firstTime.plusMinutes(17), 2.0d);
        SQLiteDatabase sqlite = DB.helper().getWritableDatabase();
        final String trigger = "ci_second_new_schedule_intake_abort";
        sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
        try {
            assertEquals(1, DB.patients().create(owner));
            medicine.setPatient(owner);
            assertEquals(1, DB.medicines().create(medicine));
            schedule.setPatient(owner);
            first.setPatient(owner);
            second.setPatient(owner);
            // The first occurrence will be successfully INSERTed on SQLite.
            // The second aborts: every SQL write AND each Java generated ID
            // must be reverted so the same two dose objects can be retried.
            sqlite.execSQL("CREATE TRIGGER " + trigger
                    + " BEFORE INSERT ON EventInstances"
                    + " WHEN (SELECT COUNT(*) FROM EventInstances"
                    + " WHERE Ref = NEW.Ref) > 0"
                    + " BEGIN SELECT RAISE(ABORT, 'synthetic second dose failure'); END;");
            boolean rejected = false;
            try {
                ScheduleUtils.instance().persistNewScheduleRows(
                        InstrumentationRegistry.getInstrumentation().getTargetContext(),
                        schedule, Arrays.asList(first, second));
            } catch (RuntimeException expected) {
                rejected = true;
            }
            assertTrue("The second intake must abort the whole new prescription", rejected);
            assertNull(schedule.getId());
            assertNull(first.getId());
            assertNull(second.getId());
            assertNull(first.getRef());
            assertNull(second.getRef());
            assertEquals(owner, first.getPatient());
            assertEquals(owner, second.getPatient());
            assertTrue(DB.schedules().findByMedicine(medicine).isEmpty());
            assertTrue(DB.eventInstances().findByTypeAndTime(
                    EventType.MEDICATION_INTAKE, firstTime).isEmpty());
            assertTrue(DB.eventInstances().findByTypeAndTime(
                    EventType.MEDICATION_INTAKE, second.getTime()).isEmpty());

            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            ScheduleUtils.instance().persistNewScheduleRows(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(),
                    schedule, Arrays.asList(first, second));
            assertNotNull(schedule.getId());
            assertNotNull(first.getId());
            assertNotNull(second.getId());
            assertEquals(schedule.getId(), first.getRef());
            assertEquals(schedule.getId(), second.getRef());
            assertEquals(2, DB.eventInstances().findBy(
                    EventInstance.COLUMN_REF, schedule.getId()).size());
        } finally {
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            for (EventInstance e : new EventInstance[]{first, second}) {
                if (e.getId() != null && DB.eventInstances().findById(e.getId()) != null) {
                    DB.eventInstances().remove(e);
                }
            }
            if (schedule.getId() != null
                    && DB.schedules().findById(schedule.getId()) != null) {
                DB.schedules().remove(schedule);
            }
            if (medicine.getId() != null
                    && DB.medicines().findById(medicine.getId()) != null) {
                DB.medicines().remove(medicine);
            }
            if (owner.getId() != null
                    && DB.patients().findById(owner.getId()) != null) {
                DB.patients().remove(owner);
            }
        }
    }

    @Test
    public void failedEditedOccurrenceDeleteRollsBackStoredPrescriptionState()
            throws Exception {
        assertTrue(DB.initialized);
        String key = Long.toString(System.nanoTime());
        Patient patient = new Patient();
        patient.setName("Synthetic edited schedule owner");
        patient.setCode("ci-edit-schedule-rollback-" + key);
        Medicine medicine = new Medicine("Synthetic edited schedule medicine",
                Presentation.PILLS);
        Schedule schedule = new Schedule(medicine);
        DateTime from = DateTime.now().minusDays(4);
        DateTime obsoleteTime = from.plusDays(1).withMillisOfSecond(0);
        EventInstance obsolete = event(null, obsoleteTime, 1.0d);
        SQLiteDatabase sqlite = DB.helper().getWritableDatabase();
        final String trigger = "ci_edit_schedule_occurrence_abort";
        sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
        try {
            assertEquals(1, DB.patients().create(patient));
            medicine.setPatient(patient);
            assertEquals(1, DB.medicines().create(medicine));
            schedule.setPatient(patient);
            assertEquals(1, DB.schedules().create(schedule));
            obsolete.setPatient(patient);
            obsolete.setRef(schedule.getId());
            assertEquals(1, DB.eventInstances().create(obsolete));
            assertFalse(DB.schedules().findById(schedule.getId())
                    .hasState(Schedule.ScheduleState.BLOCKED));

            sqlite.execSQL("CREATE TRIGGER " + trigger
                    + " BEFORE DELETE ON EventInstances WHEN OLD._id = " + obsolete.getId()
                    + " BEGIN SELECT RAISE(ABORT, 'synthetic recurrence delete failure'); END;");
            schedule.addState(Schedule.ScheduleState.BLOCKED);
            try {
                ScheduleUtils.instance().persistEditedScheduleRows(
                        InstrumentationRegistry.getInstrumentation().getTargetContext(),
                        schedule, from, java.util.Collections.emptyList());
                fail("Rejected occurrence deletion must reject prescription edit");
            } catch (RuntimeException expected) {
                // Both the model UPDATE and event DELETE must be rolled back.
            }
            Schedule fromSqlite = DB.schedules().findById(schedule.getId());
            assertNotNull(fromSqlite);
            assertEquals(1, DB.schedules().refresh(fromSqlite));
            assertFalse("Uncommitted blocked state must not survive rollback",
                    fromSqlite.hasState(Schedule.ScheduleState.BLOCKED));
            assertNotNull("Previous pending intake must survive the failed edit",
                    DB.eventInstances().findById(obsolete.getId()));
            assertEquals(1.0d, DB.eventInstances().findById(obsolete.getId())
                    .getDoubleParam(EventInstance.PARAM_DOSE), 0.0001d);
        } finally {
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            if (obsolete.getId() != null && DB.eventInstances().findById(obsolete.getId()) != null) {
                DB.eventInstances().remove(obsolete);
            }
            if (schedule.getId() != null && DB.schedules().findById(schedule.getId()) != null) {
                DB.schedules().remove(schedule);
            }
            if (medicine.getId() != null && DB.medicines().findById(medicine.getId()) != null) {
                DB.medicines().remove(medicine);
            }
            if (patient.getId() != null && DB.patients().findById(patient.getId()) != null) {
                DB.patients().remove(patient);
            }
        }
    }

    @Test
    public void recycledPatientOrMedicineIdCannotEditAnotherOwnersSchedule()
            throws Exception {
        assertTrue(DB.initialized);
        String suffix = Long.toString(System.nanoTime());
        Patient owner = new Patient();
        owner.setCode("ci-schedule-stable-owner-" + suffix);
        owner.setName("Synthetic schedule owner");
        Medicine medicine = new Medicine("Synthetic guarded schedule medicine",
                Presentation.PILLS);
        Schedule schedule = new Schedule(medicine);
        DateTime before = DateTime.now().minusDays(4);
        DateTime oldDoseTime = before.plusDays(1).withMillisOfSecond(0);
        EventInstance previous = event(null, oldDoseTime, 1.0d);
        try {
            assertEquals(1, DB.patients().create(owner));
            medicine.setPatient(owner);
            assertEquals(1, DB.medicines().create(medicine));
            schedule.setPatient(owner);
            assertEquals(1, DB.schedules().create(schedule));
            previous.setRef(schedule.getId());
            previous.setPatient(owner);
            assertEquals(1, DB.eventInstances().create(previous));

            Patient recycled = new Patient();
            recycled.setId(owner.getId());
            recycled.setCode("ci-restored-different-owner-" + suffix);
            schedule.setPatient(recycled);
            try {
                ScheduleUtils.instance().persistEditedScheduleRows(
                        InstrumentationRegistry.getInstrumentation().getTargetContext(),
                        schedule, before, Collections.emptyList());
                fail("A same-ID different-code owner must not edit a prescription");
            } catch (RuntimeException expected) {
                // The false owner is rejected BEFORE the SQL UPDATE.
            }
            assertNotNull("The prior patient's dose must survive rejected edit",
                    DB.eventInstances().findById(previous.getId()));
            assertEquals("Persisted owner must be unchanged", owner.getCode(),
                    DB.schedules().findById(schedule.getId()).getPatient().getCode());

            schedule.setPatient(owner);
            Medicine forgedMedicine = new Medicine("Synthetic old medicine object",
                    Presentation.PILLS);
            forgedMedicine.setId(medicine.getId());
            forgedMedicine.setPatient(recycled);
            schedule.setMedicine(forgedMedicine);
            try {
                ScheduleUtils.instance().persistEditedScheduleRows(
                        InstrumentationRegistry.getInstrumentation().getTargetContext(),
                        schedule, before, Collections.emptyList());
                fail("A stale same-ID medicine owner must not authorize editing");
            } catch (RuntimeException expected) {
                // The persisted medication can have the same numeric row ID.
            }
            assertNotNull("The other owner's dose must not be touched",
                    DB.eventInstances().findById(previous.getId()));

            // Control case: the original stable owner is valid. No obsolete
            // dose may survive the successful recurrence reconciliation.
            schedule.setMedicine(medicine);
            ScheduleUtils.instance().persistEditedScheduleRows(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(),
                    schedule, before, Collections.emptyList());
            assertNull(DB.eventInstances().findById(previous.getId()));
        } finally {
            if (previous.getId() != null
                    && DB.eventInstances().findById(previous.getId()) != null) {
                DB.eventInstances().remove(previous);
            }
            if (schedule.getId() != null && DB.schedules().findById(schedule.getId()) != null) {
                DB.schedules().remove(schedule);
            }
            if (medicine.getId() != null && DB.medicines().findById(medicine.getId()) != null) {
                DB.medicines().remove(medicine);
            }
            if (owner.getId() != null && DB.patients().findById(owner.getId()) != null) {
                DB.patients().remove(owner);
            }
        }
    }

    /**
     * An in-progress HIGH-alert reminder replay must not disappear when a
     * stale schedule editor saves an otherwise valid recurrence. This would
     * permanently lose the only crash-recovery token for its future doses.
     */
    @Test
    public void staleScheduleEditCannotEraseDurablePendingRecovery()
            throws Exception {
        assertTrue(DB.initialized);
        String suffix = Long.toString(System.nanoTime());
        Patient owner = new Patient();
        owner.setCode("ci-recovery-edit-owner-" + suffix);
        owner.setName("Synthetic recovery edit owner");
        Medicine medicine = new Medicine("Synthetic recovery edit medicine",
                Presentation.PILLS);
        Schedule proposed = new Schedule(medicine);
        EventInstance pending = null;
        try {
            assertEquals(1, DB.patients().create(owner));
            medicine.setPatient(owner);
            assertEquals(1, DB.medicines().create(medicine));
            proposed.setPatient(owner);
            assertEquals(1, DB.schedules().create(proposed));

            DateTime from = DateTime.now().minusDays(4);
            pending = event(proposed.getId(),
                    from.plusDays(1).withMillisOfSecond(0), 1.0d);
            pending.setPatient(owner);
            assertEquals(1, DB.eventInstances().create(pending));

            // The editor's Java instance was loaded before the HIGH warning
            // was removed. Only the persisted SQLite row has the marker.
            Schedule stored = new Schedule();
            stored.setId(proposed.getId());
            assertEquals(1, DB.schedules().refresh(stored));
            // Set the on-disk state directly in this disposable fixture.
            // The production code still uses an owner-validated DAO
            // transaction; this setup must not depend on ORMLite reporting
            // a changed-row count for an artificial stale Java object.
            android.database.sqlite.SQLiteDatabase sqlite =
                    DB.helper().getWritableDatabase();
            sqlite.execSQL("UPDATE NewSchedules SET State=?, RecoveryOwnerDigest=?"
                            + " WHERE _id=?",
                    new Object[]{"[\\\"PENDING_REMINDER_RECOVERY\\\"]",
                            ScheduleAlarmRecovery.ownerDigest(owner), proposed.getId()});
            assertTrue(DB.schedules().findById(proposed.getId())
                    .hasState(Schedule.ScheduleState.PENDING_REMINDER_RECOVERY));

            try {
                ScheduleUtils.instance().persistEditedScheduleRows(
                        InstrumentationRegistry.getInstrumentation().getTargetContext(),
                        proposed, from, Collections.emptyList());
                fail("Stale schedule edit silently discarded recovery obligation");
            } catch (RuntimeException expected) {
                // Before applying SQL changes, require pending alarm replay.
            }
            Schedule preserved = DB.schedules().findById(proposed.getId());
            assertTrue(preserved.hasState(
                    Schedule.ScheduleState.PENDING_REMINDER_RECOVERY));
            assertEquals(ScheduleAlarmRecovery.ownerDigest(owner),
                    preserved.getRecoveryOwnerDigest());
            assertNotNull("Pending intake must survive rejected edit",
                    DB.eventInstances().findById(pending.getId()));

            // A recovery path can later clear its OWN token, after which the
            // same legitimate schedule edit is allowed.
            stored = new Schedule();
            stored.setId(proposed.getId());
            assertEquals(1, DB.schedules().refresh(stored));
            sqlite.execSQL("UPDATE NewSchedules SET State=?, RecoveryOwnerDigest=NULL"
                            + " WHERE _id=?",
                    new Object[]{"[]", proposed.getId()});
            assertFalse(DB.schedules().findById(proposed.getId())
                    .hasState(Schedule.ScheduleState.PENDING_REMINDER_RECOVERY));
            ScheduleUtils.instance().persistEditedScheduleRows(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(),
                    proposed, from, Collections.emptyList());
            assertNull(DB.eventInstances().findById(pending.getId()));
        } finally {
            if (pending != null && pending.getId() != null
                    && DB.eventInstances().findById(pending.getId()) != null) {
                DB.eventInstances().remove(pending);
            }
            if (proposed.getId() != null
                    && DB.schedules().findById(proposed.getId()) != null) {
                DB.schedules().remove(proposed);
            }
            if (medicine.getId() != null
                    && DB.medicines().findById(medicine.getId()) != null) {
                DB.medicines().remove(medicine);
            }
            if (owner.getId() != null
                    && DB.patients().findById(owner.getId()) != null) {
                DB.patients().remove(owner);
            }
        }
    }

    @Test
    public void failedObsoleteDeleteRollsBackEarlierDoseUpdateAndNewOccurrences() {
        assertTrue(DB.initialized);
        Schedule schedule = new Schedule();
        schedule.setId(-System.nanoTime());
        DateTime from = DateTime.now().minusDays(6);
        DateTime existingTime = from.plusDays(1).withMillisOfSecond(0);
        DateTime obsoleteTime = from.plusDays(2).withMillisOfSecond(0);
        DateTime newTime = from.plusDays(3).withMillisOfSecond(0);
        EventInstance existing = event(schedule.getId(), existingTime, 1.0d);
        EventInstance obsolete = event(schedule.getId(), obsoleteTime, 2.0d);
        EventInstance revised = event(schedule.getId(), existingTime, 5.0d);
        EventInstance added = event(schedule.getId(), newTime, 3.0d);
        SQLiteDatabase sqlite = DB.helper().getWritableDatabase();
        final String trigger = "ci_test_recurrence_delete_abort";
        sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
        try {
            DB.eventInstances().save(existing);
            DB.eventInstances().save(obsolete);
            assertNotNull(existing.getId());
            assertNotNull(obsolete.getId());
            sqlite.execSQL("CREATE TRIGGER " + trigger
                    + " BEFORE DELETE ON EventInstances WHEN OLD._id = " + obsolete.getId()
                    + " BEGIN SELECT RAISE(ABORT, 'synthetic recurrence failure'); END;");
            try {
                ScheduleUtils.instance().reconcileScheduleOccurrences(
                        InstrumentationRegistry.getInstrumentation().getTargetContext(),
                        schedule, from, Arrays.asList(revised, added));
                fail("Failed pruning must roll back earlier recurrence changes");
            } catch (RuntimeException expected) {
                // The previous event update must not survive this later failure.
            }
            assertEquals("Original pending dose must survive rollback",
                    1.0d, DB.eventInstances().findById(existing.getId())
                            .getDoubleParam(EventInstance.PARAM_DOSE), 0.0001d);
            assertNotNull("Obsolete pending row must survive rollback",
                    DB.eventInstances().findById(obsolete.getId()));
            assertFalse("New time must not have been inserted before SQL COMMIT",
                    DB.eventInstances().exists(EventType.MEDICATION_INTAKE,
                            newTime, schedule.getId()));

            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            ScheduleUtils.instance().reconcileScheduleOccurrences(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(),
                    schedule, from, Arrays.asList(revised, added));
            assertEquals("Retry updates original pending dose",
                    5.0d, DB.eventInstances().findById(existing.getId())
                            .getDoubleParam(EventInstance.PARAM_DOSE), 0.0001d);
            assertNull("Retry removes the obsolete pending row",
                    DB.eventInstances().findById(obsolete.getId()));
            assertTrue("Retry adds the new occurrence once",
                    DB.eventInstances().exists(EventType.MEDICATION_INTAKE,
                            newTime, schedule.getId()));
        } finally {
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            for (EventInstance row : DB.eventInstances().findByTypeAndTime(
                    EventType.MEDICATION_INTAKE, newTime)) {
                if (schedule.getId().equals(row.getRef())) {
                    DB.eventInstances().remove(row);
                }
            }
            for (EventInstance row : new EventInstance[]{existing, obsolete}) {
                if (row.getId() != null && DB.eventInstances().findById(row.getId()) != null) {
                    DB.eventInstances().remove(row);
                }
            }
        }
    }

    private static EventInstance event(Long scheduleId, DateTime time, double dose) {
        EventInstance event = new EventInstance(time, EventType.MEDICATION_INTAKE);
        event.setRef(scheduleId);
        event.addParam(EventInstance.PARAM_DOSE, dose);
        return event;
    }
}
