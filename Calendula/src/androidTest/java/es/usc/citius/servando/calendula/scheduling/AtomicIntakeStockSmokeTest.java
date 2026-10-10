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
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Schedule;
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
import static org.junit.Assert.fail;

/**
 * Genuine SQLite and optional OS alarms; only synthetic, disposable fixtures.
 * Test SQLite trigger failures in both the stock and event persistence steps.
 */
@RunWith(AndroidJUnit4.class)
public class AtomicIntakeStockSmokeTest {
    private static final float INITIAL_STOCK = 10.0f;
    private static final float DOSE = 2.0f;

    private static final class Fixture {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final DateTime when = DateTime.now().plusHours(13).withMillisOfSecond(0);
        final Patient patient = new Patient();
        final Medicine medicine = new Medicine("Synthetic stock transaction medicine", Presentation.PILLS);
        final Schedule schedule = new Schedule(medicine);
        final EventInstance event = new EventInstance(when, EventType.MEDICATION_INTAKE);

        Fixture() throws Exception {
            patient.setCode("ci-atomic-owned-stock-" + System.nanoTime());
            patient.setName("Synthetic stock transaction patient");
            assertEquals(1, DB.patients().create(patient));
            medicine.setPatient(patient);
            schedule.setPatient(patient);
            event.setPatient(patient);
            medicine.setStock(INITIAL_STOCK);
            // Use raw ORM create to avoid emitting stock alerts while building
            // a deliberately incomplete synthetic recurrence fixture.
            assertEquals(1, DB.medicines().create(medicine));
            assertNotNull(medicine.getId());
            assertEquals(1, DB.schedules().create(schedule));
            assertNotNull(schedule.getId());
            event.setRef(schedule.getId());
            event.addParam(EventInstance.PARAM_DOSE, (double) DOSE);
            assertEquals(1, DB.eventInstances().create(event));
            assertNotNull(event.getId());
        }

        float persistedStock() {
            return DB.medicines().findById(medicine.getId()).getStock();
        }

        EventInstance persistedIntake() {
            return DB.eventInstances().findById(event.getId());
        }

        void cleanup() {
            EventReminder reminder = DB.eventReminders().findBy(
                    EventType.MEDICATION_INTAKE, when, patient);
            if (reminder != null) {
                Agenda.instance().cancelAlarm(context, reminder);
                DB.eventReminders().remove(reminder);
            }
            if (event.getId() != null && DB.eventInstances().findById(event.getId()) != null) {
                DB.eventInstances().remove(event);
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
    public void confirmingAndUndoingIntakeAdjustsInventoryExactlyOnce() throws Exception {
        assertTrue(DB.initialized);
        Fixture x = new Fixture();
        try {
            assertTrue(ScheduleUtils.instance().setIntakeCompleted(x.context, x.event, true));
            assertEquals(INITIAL_STOCK - DOSE, x.persistedStock(), 0.001f);
            assertTrue(x.persistedIntake().completed());

            assertFalse("Repeated confirmation must not deduct stock twice",
                    ScheduleUtils.instance().setIntakeCompleted(x.context, x.event, true));
            assertEquals(INITIAL_STOCK - DOSE, x.persistedStock(), 0.001f);

            assertTrue(ScheduleUtils.instance().setIntakeCompleted(x.context, x.event, false));
            assertEquals(INITIAL_STOCK, x.persistedStock(), 0.001f);
            assertFalse(x.persistedIntake().completed());
        } finally {
            x.cleanup();
        }
    }

    @Test
    public void medicineUpdateSqlFailureRollsBackIntake() throws Exception {
        assertRollbackOnInjectedFailure("Medicines", "medicine_update", true);
    }

    @Test
    public void intakeUpdateSqlFailureRollsBackMedicineStock() throws Exception {
        assertRollbackOnInjectedFailure("EventInstances", "intake_update", false);
    }

    private void assertRollbackOnInjectedFailure(
            String table, String triggerSuffix, boolean stockWrite) throws Exception {
        assertTrue(DB.initialized);
        Fixture x = new Fixture();
        SQLiteDatabase sqlite = DB.helper().getWritableDatabase();
        final String trigger = "ci_test_atomic_" + triggerSuffix;
        sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
        try {
            final long pk = stockWrite ? x.medicine.getId() : x.event.getId();
            sqlite.execSQL("CREATE TRIGGER " + trigger + " BEFORE UPDATE ON " + table
                    + " WHEN OLD._id = " + pk
                    + " BEGIN SELECT RAISE(ABORT, 'synthetic atomic transaction failure'); END;");
            try {
                ScheduleUtils.instance().setIntakeCompleted(x.context, x.event, true);
                fail("Injected SQLite failure must abort the whole transaction");
            } catch (RuntimeException expected) {
                // TransactionManager must revert the entire medicine/event pair.
            }
            assertEquals("Failed transaction cannot deduct medicine stock",
                    INITIAL_STOCK, x.persistedStock(), 0.001f);
            assertFalse("Failed transaction cannot mark a dose taken",
                    x.persistedIntake().completed());
            assertFalse("A failed transaction must not alter the caller's record",
                    x.event.completed());
        } finally {
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            x.cleanup();
        }
    }

    @Test
    public void aPatientCannotDebitAnotherPatientsSchedule() throws Exception {
        assertTrue(DB.initialized);
        Fixture x = new Fixture();
        Patient other = new Patient();
        other.setCode("ci-foreign-stock-" + System.nanoTime());
        other.setName("Synthetic unrelated patient");
        try {
            DB.patients().save(other);
            x.event.setPatient(other);
            DB.eventInstances().update(x.event);
            try {
                ScheduleUtils.instance().setIntakeCompleted(x.context, x.event, true);
                fail("Mismatched event and schedule ownership must abort");
            } catch (RuntimeException expected) {
                // SQL event and medicine stock remain unchanged.
            }
            assertFalse(x.persistedIntake().completed());
            assertEquals(INITIAL_STOCK, x.persistedStock(), 0.001f);
            assertFalse(x.event.completed());
        } finally {
            x.cleanup();
            if (other.getId() != null) DB.patients().remove(other);
        }
    }

    @Test
    public void reassigningMedicineToAnotherPatientNeverDebitsThatInventory() throws Exception {
        assertTrue(DB.initialized);
        Fixture x = new Fixture();
        Patient newOwner = new Patient();
        newOwner.setCode("ci-medicine-reassigned-" + System.nanoTime());
        newOwner.setName("Synthetic unrelated medicine owner");
        try {
            DB.patients().save(newOwner);
            // A medication event and its schedule still belong to A, but the
            // same persisted medicine has been reassigned to B after these
            // fixture objects were created. Do not trust a cached owner.
            assertNotNull(newOwner.getId());
            assertEquals("Fixture cache still claims A owns the medicine",
                    x.patient.getId(), x.medicine.getPatient().getId());
            // Simulate an independent SQLite change, not an in-memory ORM
            // update: Android's ORMLite update() may report zero changed rows
            // for an ownership rewrite and would not create this stale cache.
            DB.helper().getWritableDatabase().execSQL(
                    "UPDATE Medicines SET Patient = ? WHERE _id = ?",
                    new Object[]{newOwner.getId(), x.medicine.getId()});
            assertEquals("The authoritative medicine owner is now B",
                    newOwner.getId(),
                    DB.medicines().findById(x.medicine.getId()).getPatient().getId());

            try {
                ScheduleUtils.instance().setIntakeCompleted(x.context, x.event, true);
                fail("An intake for A must not deduct a medicine reassigned to B");
            } catch (RuntimeException expected) {
                // Patient ownership must be validated against refreshed SQLite.
            }
            assertEquals("Another patient's stock remains untouched",
                    INITIAL_STOCK, x.persistedStock(), 0.001f);
            assertFalse("No dose can be confirmed after cross-patient reassignment",
                    x.persistedIntake().completed());
            assertFalse("The caller must not observe a failed intake as successful",
                    x.event.completed());
        } finally {
            x.cleanup();
            if (newOwner.getId() != null) DB.patients().remove(newOwner);
        }
    }

    @Test
    public void unassignedIntakeCannotDebitTrackedStock() throws Exception {
        assertTrue(DB.initialized);
        Fixture x = new Fixture();
        try {
            x.event.setPatient(null);
            DB.eventInstances().update(x.event);
            try {
                ScheduleUtils.instance().setIntakeCompleted(x.context, x.event, true);
                fail("Medication stock cannot be debited for an unassigned patient");
            } catch (RuntimeException expected) {
                // The missing owner must stop all changes before SQL persistence.
            }
            assertFalse("Unassigned intake cannot be marked taken",
                    x.persistedIntake().completed());
            assertEquals("No stock may be deducted without a patient",
                    INITIAL_STOCK, x.persistedStock(), 0.001f);
            assertFalse(x.event.completed());
        } finally {
            x.cleanup();
        }
    }

    @Test
    public void unassignedScheduleCannotDebitPatientsStock() throws Exception {
        assertTrue(DB.initialized);
        Fixture x = new Fixture();
        try {
            x.schedule.setPatient(null);
            DB.schedules().update(x.schedule);
            try {
                ScheduleUtils.instance().setIntakeCompleted(x.context, x.event, true);
                fail("Medication schedule without assigned patient cannot debit stock");
            } catch (RuntimeException expected) {
                // SQL must remain unchanged for both event and stock.
            }
            assertFalse(x.persistedIntake().completed());
            assertEquals(INITIAL_STOCK, x.persistedStock(), 0.001f);
        } finally {
            x.cleanup();
        }
    }

    @Test
    public void fullyOrphanedTrackedIntakeCannotDebitStock() throws Exception {
        assertTrue(DB.initialized);
        Fixture x = new Fixture();
        try {
            // This was the actual gap: three nullable owners compared equal,
            // allowing a debit on a tracked medicine with no known patient.
            x.event.setPatient(null);
            x.schedule.setPatient(null);
            x.medicine.setPatient(null);
            DB.medicines().update(x.medicine);
            DB.schedules().update(x.schedule);
            DB.eventInstances().update(x.event);
            try {
                ScheduleUtils.instance().setIntakeCompleted(x.context, x.event, true);
                fail("Null/null ownership cannot authorize a managed stock debit");
            } catch (RuntimeException expected) {
                // Neither intake state nor medicine stock can change.
            }
            assertFalse("Orphaned tracked dose must remain uncompleted",
                    x.persistedIntake().completed());
            assertEquals("Orphaned tracked stock must remain unchanged",
                    INITIAL_STOCK, x.persistedStock(), 0.001f);
        } finally {
            x.cleanup();
        }
    }

    @Test
    public void legacyUnassignedIntakeWithoutStockManagementCanStillBeRecorded() throws Exception {
        assertTrue(DB.initialized);
        Fixture x = new Fixture();
        try {
            // Historical records can have no patient and no tracked stock.
            // Completing them must remain possible without inventing owners
            // or manufacturing an inventory balance.
            x.event.setPatient(null);
            x.schedule.setPatient(null);
            x.medicine.setPatient(null);
            x.medicine.setStock(null);
            DB.medicines().update(x.medicine);
            DB.schedules().update(x.schedule);
            DB.eventInstances().update(x.event);
            assertTrue("Untracked legacy intake remains acknowledgeable",
                    ScheduleUtils.instance().setIntakeCompleted(x.context, x.event, true));
            assertTrue(x.persistedIntake().completed());
            assertNull("Untracked stock must not be synthesized",
                    DB.medicines().findById(x.medicine.getId()).getStock());
        } finally {
            x.cleanup();
        }
    }

    @Test
    public void missingScheduleCannotConsumeStockOrMarkIntakeTaken() throws Exception {
        assertTrue(DB.initialized);
        Fixture x = new Fixture();
        try {
            x.event.setRef(9000007701L);
            DB.eventInstances().update(x.event);
            try {
                ScheduleUtils.instance().setIntakeCompleted(x.context, x.event, true);
                fail("Missing schedule must never be assumed to have a valid dose");
            } catch (RuntimeException expected) {
                // A broken foreign reference must not modify stock or intake.
            }
            assertFalse(x.persistedIntake().completed());
            assertEquals(INITIAL_STOCK, x.persistedStock(), 0.001f);
        } finally {
            x.cleanup();
        }
    }

    @Test
    public void insufficientStockCannotProduceNegativeInventory() throws Exception {
        assertTrue(DB.initialized);
        Fixture x = new Fixture();
        try {
            x.medicine.setStock(1.0f);
            DB.medicines().update(x.medicine);
            try {
                ScheduleUtils.instance().setIntakeCompleted(x.context, x.event, true);
                fail("A negative inventory adjustment must not be committed");
            } catch (RuntimeException expected) {
                // No negative inventory or completed intake may be persisted.
            }
            assertEquals(1.0f, x.persistedStock(), 0.001f);
            assertFalse(x.persistedIntake().completed());
        } finally {
            x.cleanup();
        }
    }

    @Test
    public void invalidOrMissingDoseMustNotConsumeStockOrMarkIntakeTaken() throws Exception {
        assertTrue(DB.initialized);
        Fixture x = new Fixture();
        try {
            x.event.addParam(EventInstance.PARAM_DOSE, "not-a-number");
            DB.eventInstances().update(x.event);
            try {
                ScheduleUtils.instance().setIntakeCompleted(x.context, x.event, true);
                fail("An invalid dose must not be recorded as a successful stock-managed intake");
            } catch (RuntimeException expected) {
                // Error propagates for UI/error reporting instead of being swallowed.
            }
            assertEquals(INITIAL_STOCK, x.persistedStock(), 0.001f);
            assertFalse(x.persistedIntake().completed());
        } finally {
            x.cleanup();
        }
    }

    /**
     * Two independently stocked, same-time doses belonging to one synthetic
     * patient. All rows and Android tokens are removed after every test.
     */
    private static final class BatchFixture {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final DateTime time = DateTime.now().plusHours(11).withMillisOfSecond(0);
        final Patient patient = new Patient();
        final Medicine[] medicines = {
                new Medicine("Synthetic batch medicine A", Presentation.PILLS),
                new Medicine("Synthetic batch medicine B", Presentation.PILLS)};
        final Schedule[] schedules = {new Schedule(medicines[0]), new Schedule(medicines[1])};
        final EventInstance[] events = {
                new EventInstance(time, EventType.MEDICATION_INTAKE),
                new EventInstance(time, EventType.MEDICATION_INTAKE)};
        final EventReminder reminder = new EventReminder(time, EventType.MEDICATION_INTAKE);

        BatchFixture() throws Exception {
            this(false);
        }

        BatchFixture(boolean sharedStock) throws Exception {
            patient.setCode("ci-atomic-batch-" + System.nanoTime());
            patient.setName("Synthetic isolated patient");
            assertEquals(1, DB.patients().create(patient));
            for (int i = 0; i < 2; i++) {
                medicines[i].setPatient(patient);
                medicines[i].setStock(sharedStock && i == 0
                        ? DOSE + 0.5f : INITIAL_STOCK);
                assertEquals(1, DB.medicines().create(medicines[i]));
                schedules[i].setPatient(patient);
                assertEquals(1, DB.schedules().create(schedules[i]));
                events[i].setPatient(patient);
                events[i].setRef(schedules[sharedStock ? 0 : i].getId());
                events[i].addParam(EventInstance.PARAM_DOSE, (double) DOSE);
                assertEquals(1, DB.eventInstances().create(events[i]));
                assertNotNull(events[i].getId());
            }
            reminder.setPatient(patient);
            reminder.setNextTime(time);
            assertEquals(1, DB.eventReminders().create(reminder));
            Agenda.instance().setAlarm(context, reminder);
        }

        float stock(int index) {
            return DB.medicines().findById(medicines[index].getId()).getStock();
        }

        boolean completed(int index) {
            return DB.eventInstances().findById(events[index].getId()).completed();
        }

        PendingIntent currentAlarm() {
            return PendingIntent.getBroadcast(context, 0,
                    Agenda.reminderBroadcastIntent(context, reminder),
                    PendingIntentFlags.immutable(PendingIntent.FLAG_NO_CREATE));
        }

        void cleanup() {
            if (reminder.getId() != null) {
                Agenda.instance().cancelAlarm(context, reminder);
                if (DB.eventReminders().findById(reminder.getId()) != null) {
                    DB.eventReminders().remove(reminder);
                }
            }
            for (EventInstance event : events) {
                if (event.getId() != null && DB.eventInstances().findById(event.getId()) != null) {
                    DB.eventInstances().remove(event);
                }
            }
            for (Schedule schedule : schedules) {
                if (schedule.getId() != null && DB.schedules().findById(schedule.getId()) != null) {
                    DB.schedules().remove(schedule);
                }
            }
            for (Medicine medicine : medicines) {
                if (medicine.getId() != null && DB.medicines().findById(medicine.getId()) != null) {
                    DB.medicines().remove(medicine);
                }
            }
            if (patient.getId() != null && DB.patients().findById(patient.getId()) != null) {
                DB.patients().remove(patient);
            }
        }
    }

    @Test
    public void confirmAllCommitsBothStockBalancesAndEventsExactlyOnce() throws Exception {
        assertTrue(DB.initialized);
        BatchFixture x = new BatchFixture();
        try {
            assertNotNull("The synthetic reminder is initially registered", x.currentAlarm());
            assertEquals(2, ScheduleUtils.instance().checkIntakeEvents(x.context, x.patient, x.time));
            for (int i = 0; i < 2; i++) {
                assertTrue("Every dose must be committed", x.completed(i));
                assertEquals(INITIAL_STOCK - DOSE, x.stock(i), 0.001f);
            }
            assertEquals("Repeating confirmation cannot consume stock twice",
                    0, ScheduleUtils.instance().checkIntakeEvents(x.context, x.patient, x.time));
            for (int i = 0; i < 2; i++) {
                assertEquals(INITIAL_STOCK - DOSE, x.stock(i), 0.001f);
            }
            assertFalse("After all doses commit there is no pending reminder row",
                    DB.eventReminders().findById(x.reminder.getId()) != null);
        } finally {
            x.cleanup();
        }
    }

    @Test
    public void secondDoseSqlFailureRollsBackFirstDoseAndKeepsAlarm() throws Exception {
        assertTrue(DB.initialized);
        BatchFixture x = new BatchFixture();
        SQLiteDatabase sqlite = DB.helper().getWritableDatabase();
        final String trigger = "ci_test_atomic_batch_abort_second";
        sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
        try {
            assertNotNull(x.currentAlarm());
            // The batch processes primary keys in ascending order. Trigger
            // on the later event so the first debit has actually occurred.
            assertTrue(x.events[0].getId() < x.events[1].getId());
            sqlite.execSQL("CREATE TRIGGER " + trigger
                    + " BEFORE UPDATE ON EventInstances WHEN OLD._id = " + x.events[1].getId()
                    + " BEGIN SELECT RAISE(ABORT, 'synthetic second dose failure'); END;");
            boolean aborted = false;
            try {
                ScheduleUtils.instance().checkIntakeEvents(x.context, x.patient, x.time);
            } catch (RuntimeException expected) {
                aborted = true;
            }
            assertTrue("The entire bulk confirmation must reject the failed dose", aborted);
            for (int i = 0; i < 2; i++) {
                assertFalse("Both dose flags must roll back after later failure", x.completed(i));
                assertEquals("No individual stock deduction may survive a bulk rollback",
                        INITIAL_STOCK, x.stock(i), 0.001f);
            }
            assertNotNull("Rollback must retain the existing Android alarm", x.currentAlarm());
            assertNotNull("Rollback must retain the persisted reminder",
                    DB.eventReminders().findById(x.reminder.getId()));

            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            assertEquals("Retry must now commit the entire group",
                    2, ScheduleUtils.instance().checkIntakeEvents(x.context, x.patient, x.time));
            for (int i = 0; i < 2; i++) {
                assertTrue(x.completed(i));
                assertEquals(INITIAL_STOCK - DOSE, x.stock(i), 0.001f);
            }
        } finally {
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            x.cleanup();
        }
    }


    @Test
    public void sharedStockShortageRollsBackAllConfirmedDoses() throws Exception {
        assertTrue(DB.initialized);
        BatchFixture x = new BatchFixture(true);
        try {
            // Both synthetic events refer to the first schedule's medicine,
            // whose starting stock covers one dose but not the full batch.
            boolean rejected = false;
            try {
                ScheduleUtils.instance().checkIntakeEvents(x.context, x.patient, x.time);
            } catch (RuntimeException expected) {
                rejected = true;
            }
            assertTrue("Insufficient shared stock must reject the entire confirmation", rejected);
            assertFalse(x.completed(0));
            assertFalse(x.completed(1));
            assertEquals(DOSE + 0.5f, x.stock(0), 0.001f);
            assertEquals(INITIAL_STOCK, x.stock(1), 0.001f);
            assertNotNull("The original medication alarm must remain scheduled", x.currentAlarm());
        } finally {
            x.cleanup();
        }
    }

}
