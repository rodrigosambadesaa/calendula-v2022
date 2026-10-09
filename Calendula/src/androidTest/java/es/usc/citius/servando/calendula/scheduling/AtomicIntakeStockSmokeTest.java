/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
        final Medicine medicine = new Medicine("Synthetic stock transaction medicine", Presentation.PILLS);
        final Schedule schedule = new Schedule(medicine);
        final EventInstance event = new EventInstance(when, EventType.MEDICATION_INTAKE);

        Fixture() throws Exception {
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
                    EventType.MEDICATION_INTAKE, when, null);
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
}
