/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util.alerts;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.dmfs.rfc5545.recur.Freq;
import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import es.usc.citius.servando.calendula.allergies.AllergenType;
import es.usc.citius.servando.calendula.allergies.AllergenVO;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.PatientAlert;
import es.usc.citius.servando.calendula.persistence.PatientAllergen;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.scheduling.model.recur.RecurringEvent;
import es.usc.citius.servando.calendula.persistence.alerts.AllergyPatientAlert;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

import static org.junit.Assert.*;

/**
 * Disposable emulator SQLite only: creates synthetic owner/medication/allergy
 * fixtures and never reads or modifies real medical records.
 */
@RunWith(AndroidJUnit4.class)
public class AtomicAllergenRegistrationSmokeTest {

    private static final class Fixture implements AutoCloseable {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final Patient owner = new Patient();
        final String suffix = Long.toString(System.nanoTime());
        final SharedPreferences prefs = PreferenceUtils.instance().preferences();
        final String[] keys = {
                PreferenceKeys.PATIENTS_ACTIVE.key(),
                PreferenceKeys.PATIENTS_ACTIVE_OWNER.key(),
                PreferenceKeys.PATIENTS_RESELECTION_REQUIRED.key()
        };
        final Object[] previous = new Object[keys.length];
        Medicine medicine;

        Fixture(boolean createMedicine) throws Exception {
            assertTrue(DB.initialized);
            for (int i = 0; i < keys.length; i++) {
                previous[i] = prefs.getAll().get(keys[i]);
            }
            owner.setCode("ci-allergen-batch-owner-" + suffix);
            owner.setName("Synthetic disposable owner");
            assertEquals(1, DB.patients().create(owner));
            DB.patients().setActive(owner);
            if (createMedicine) {
                medicine = new Medicine("Synthetic batch medicine", Presentation.PILLS);
                medicine.setPatient(owner);
                assertEquals(1, DB.medicines().create(medicine));
            }
        }

        PatientAllergen allergen(String suffixName) {
            return new PatientAllergen(new AllergenVO(
                    AllergenType.EXCIPIENT, "Synthetic " + suffixName,
                    "ci-batch-" + suffix + "-" + suffixName), owner);
        }

        List<PatientAllergen> savedAllergens() {
            return DB.patientAllergens().findBy(
                    PatientAllergen.COLUMN_PATIENT, owner.getId());
        }

        List<PatientAlert> savedWarnings() {
            return medicine == null ? Collections.emptyList()
                    : DB.alerts().findByMedicineAndLevel(
                            medicine, PatientAlert.Level.HIGH);
        }

        Schedule createRunnableSchedule() throws Exception {
            RecurringEvent recurrence = new RecurringEvent.Builder()
                    .from(DateTime.now().minusDays(1))
                    .repeatEvery(12, Freq.HOURLY).build();
            Schedule schedule = new Schedule(recurrence);
            schedule.setPatient(owner);
            schedule.setMedicine(medicine);
            schedule.setDosage(1.0d);
            assertEquals(1, DB.schedules().create(schedule));
            return schedule;
        }

        AllergyPatientAlert persistExistingWarning() throws Exception {
            PatientAllergen existing = allergen("previous");
            assertEquals(1, DB.patientAllergens().create(existing));
            AllergyPatientAlert warning = new AllergyPatientAlert(medicine,
                    Collections.singletonList(new AllergenVO(existing)));
            // Simulate a legacy/restored HIGH warning whose schedule was
            // subsequently created and has not yet been re-blocked.
            assertEquals(1, DB.alerts().create(warning));
            return warning;
        }

        @Override
        public void close() throws Exception {
            if (medicine != null && medicine.getId() != null
                    && DB.medicines().findById(medicine.getId()) != null) {
                for (PatientAlert warning : savedWarnings()) {
                    DB.alerts().deleteById(warning.id());
                }
            }
            for (PatientAllergen allergen : savedAllergens()) {
                DB.patientAllergens().deleteById(allergen.getId());
            }
            if (medicine != null && medicine.getId() != null
                    && DB.medicines().findById(medicine.getId()) != null) {
                for (Schedule schedule : DB.schedules().findByMedicine(medicine)) {
                    DB.eventInstances().removeByRef(schedule.getId());
                    DB.schedules().deleteById(schedule.getId());
                }
            }
            if (medicine != null && medicine.getId() != null
                    && DB.medicines().findById(medicine.getId()) != null) {
                DB.medicines().deleteById(medicine.getId());
            }
            SharedPreferences.Editor editor = prefs.edit();
            for (int i = 0; i < keys.length; i++) {
                Object value = previous[i];
                if (value == null) editor.remove(keys[i]);
                else if (value instanceof String) editor.putString(keys[i], (String) value);
                else if (value instanceof Long) editor.putLong(keys[i], (Long) value);
                else if (value instanceof Boolean) editor.putBoolean(keys[i], (Boolean) value);
                else if (value instanceof Integer) editor.putInt(keys[i], (Integer) value);
                else if (value instanceof Float) editor.putFloat(keys[i], (Float) value);
                else throw new AssertionError("Unsupported disposable preference fixture");
            }
            assertTrue(editor.commit());
            if (owner.getId() != null && DB.patients().findById(owner.getId()) != null) {
                DB.patients().deleteById(owner.getId());
            }
        }
    }

    @Test
    public void secondConflictLookupFailureRollsBackFirstAndSecondAllergen() throws Exception {
        try (Fixture f = new Fixture(false)) {
            PatientAllergen first = f.allergen("first");
            PatientAllergen second = f.allergen("second");
            final int[] lookups = {0};
            try {
                AtomicAllergenRegistration.register(f.context, f.owner,
                        Arrays.asList(first, second), (context, allergen) -> {
                            if (++lookups[0] == 2) {
                                throw new SQLException("synthetic second lookup failure");
                            }
                            return Collections.emptyList();
                        });
                fail("Late verification failure must roll back the whole allergen batch");
            } catch (RuntimeException expected) {
                assertEquals(2, lookups[0]);
            }
            assertTrue("No first-row partial SQL commit", f.savedAllergens().isEmpty());
            assertNull("Rolled-back first object must be retryable", first.getId());
            assertNull("Rolled-back second object must be retryable", second.getId());
        }
    }

    @Test
    public void abortedSecondInsertRollsBackFirstHighWarningAndAllergen()
            throws Exception {
        try (Fixture f = new Fixture(true)) {
            PatientAllergen first = f.allergen("high");
            PatientAllergen second = f.allergen("sql-abort");
            SQLiteDatabase sqlite = DB.helper().getWritableDatabase();
            final String trigger = "ci_abort_second_batch_allergen";
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            try {
                sqlite.execSQL("CREATE TRIGGER " + trigger
                        + " BEFORE INSERT ON Allergens WHEN NEW.Identifier = '"
                        + second.getIdentifier()
                        + "' BEGIN SELECT RAISE(ABORT, 'synthetic late insert'); END;");
                try {
                    AtomicAllergenRegistration.register(f.context, f.owner,
                            Arrays.asList(first, second),
                            (context, allergen) -> Collections.singletonList(f.medicine));
                    fail("Second INSERT must abort the complete transaction");
                } catch (RuntimeException expected) {
                    // Failure occurs after first HIGH alert was inserted and
                    // its SQLite schedule-block policy had already executed.
                }
                assertTrue("No first allergen may survive the abort",
                        f.savedAllergens().isEmpty());
                assertTrue("A HIGH alert inserted before the abort must roll back",
                        f.savedWarnings().isEmpty());
                assertNull(first.getId());
                assertNull(second.getId());
            } finally {
                sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            }
        }
    }

    @Test
    public void twoAllergensForOneMedicineCreateOneCombinedHighWarning()
            throws Exception {
        try (Fixture f = new Fixture(true)) {
            PatientAllergen first = f.allergen("one");
            PatientAllergen second = f.allergen("two");
            AtomicAllergenRegistration.Result result =
                    AtomicAllergenRegistration.register(f.context, f.owner,
                            Arrays.asList(first, second),
                            (context, allergen) -> Collections.singletonList(f.medicine));
            assertTrue(result.hasConflicts());
            assertEquals(2, f.savedAllergens().size());
            List<PatientAlert> saved = f.savedWarnings();
            assertEquals("One HIGH warning for the same medicine", 1, saved.size());
            AllergyPatientAlert warning = (AllergyPatientAlert) saved.get(0).map();
            List<AllergenVO> details = warning.getDetails().getAllergens();
            assertEquals(2, details.size());
            assertTrue(details.contains(new AllergenVO(first)));
            assertTrue(details.contains(new AllergenVO(second)));
        }
    }

    @Test
    public void existingHighWarningReblocksNewlyAddedRunnableSchedule()
            throws Exception {
        try (Fixture f = new Fixture(true)) {
            f.persistExistingWarning();
            Schedule schedule = f.createRunnableSchedule();
            assertFalse("Synthetic restored schedule starts runnable",
                    DB.schedules().findById(schedule.getId())
                            .hasState(Schedule.ScheduleState.BLOCKED));

            PatientAllergen next = f.allergen("added-later");
            AtomicAllergenRegistration.Result result =
                    AtomicAllergenRegistration.register(f.context, f.owner,
                            Collections.singletonList(next),
                            (context, allergen) -> Collections.singletonList(f.medicine));
            assertTrue(result.hasConflicts());
            assertEquals(2, f.savedAllergens().size());
            assertEquals("Must update rather than duplicate HIGH warning",
                    1, f.savedWarnings().size());
            assertEquals(2, ((AllergyPatientAlert) f.savedWarnings().get(0).map())
                    .getDetails().getAllergens().size());
            assertTrue("Existing HIGH warning must also re-block future doses",
                    DB.schedules().findById(schedule.getId())
                            .hasState(Schedule.ScheduleState.BLOCKED));
        }
    }

    @Test
    public void failedReblockRollsBackExistingHighDetailsAndNewAllergen()
            throws Exception {
        try (Fixture f = new Fixture(true)) {
            f.persistExistingWarning();
            Schedule schedule = f.createRunnableSchedule();
            SQLiteDatabase sqlite = DB.helper().getWritableDatabase();
            final String trigger = "ci_abort_existing_high_reblock";
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            try {
                sqlite.execSQL("CREATE TRIGGER " + trigger
                        + " BEFORE UPDATE ON NewSchedules WHEN NEW._id = "
                        + schedule.getId()
                        + " BEGIN SELECT RAISE(ABORT, 'synthetic reblock failure'); END;");
                PatientAllergen next = f.allergen("must-rollback");
                try {
                    AtomicAllergenRegistration.register(f.context, f.owner,
                            Collections.singletonList(next),
                            (context, allergen) -> Collections.singletonList(f.medicine));
                    fail("Failed schedule block must abort whole allergy registration");
                } catch (RuntimeException expected) {
                    // A late schedule UPDATE failure must revert preceding
                    // allergen INSERT and existing HIGH-details UPDATE.
                }
                assertNull(next.getId());
                assertEquals(1, f.savedAllergens().size());
                assertEquals(1, f.savedWarnings().size());
                assertEquals("Existing warning details must remain unchanged",
                        1, ((AllergyPatientAlert) f.savedWarnings().get(0).map())
                                .getDetails().getAllergens().size());
                assertFalse("Failed block cannot appear committed",
                        DB.schedules().findById(schedule.getId())
                                .hasState(Schedule.ScheduleState.BLOCKED));
            } finally {
                sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            }
        }
    }

    @Test
    public void registrationWithinAnOuterTransactionIsRejectedBeforeAnyWrite()
            throws Exception {
        try (Fixture f = new Fixture(true)) {
            PatientAllergen candidate = f.allergen("nested-outer-transaction");
            final int[] conflictLookups = {0};
            try {
                DB.transaction(() -> {
                    assertTrue("Fixture must hold a real SQLite outer transaction",
                            DB.helper().getWritableDatabase().inTransaction());
                    AtomicAllergenRegistration.register(f.context, f.owner,
                            Collections.singletonList(candidate),
                            (context, allergen) -> {
                                conflictLookups[0]++;
                                return Collections.singletonList(f.medicine);
                            });
                    return null;
                });
                fail("An ambient transaction must not allow post-COMMIT effects");
            } catch (RuntimeException expected) {
                // The helper must reject the outer transaction at entry,
                // rather than treating a nested savepoint as a true COMMIT.
            }
            assertEquals("No catalog lookups after rejecting nested SQL",
                    0, conflictLookups[0]);
            assertNull("Candidate must remain unsaved", candidate.getId());
            assertTrue("No allergen row may be inserted", f.savedAllergens().isEmpty());
            assertTrue("No HIGH warning may be inserted", f.savedWarnings().isEmpty());
        }
    }

    @Test
    public void duplicateBatchIdentifiersAreRejectedBeforeFirstInsert()
            throws Exception {
        try (Fixture f = new Fixture(false)) {
            PatientAllergen first = f.allergen("same");
            PatientAllergen duplicate = f.allergen("same");
            try {
                AtomicAllergenRegistration.register(f.context, f.owner,
                        Arrays.asList(first, duplicate),
                        (context, allergen) -> Collections.emptyList());
                fail("Duplicate batch must be rejected before SQL");
            } catch (IllegalArgumentException expected) {
                assertTrue(f.savedAllergens().isEmpty());
            }
        }
    }
}
