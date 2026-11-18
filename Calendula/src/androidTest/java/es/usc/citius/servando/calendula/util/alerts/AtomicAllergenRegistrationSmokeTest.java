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
import es.usc.citius.servando.calendula.allergies.AllergyAlertUtil;
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
            // ORMLite creates the row via the base PatientAlert DAO; do not
            // assume the subclass fixture retains its generated row ID.
            List<PatientAlert> persisted = savedWarnings();
            assertEquals("Synthetic HIGH warning must persist", 1, persisted.size());
            assertNotNull("Synthetic HIGH warning needs an SQL row ID", persisted.get(0).id());
            return (AllergyPatientAlert) persisted.get(0).map();
        }

        @Override
        public void close() throws Exception {
            if (medicine != null && medicine.getId() != null
                    && DB.medicines().findById(medicine.getId()) != null) {
                for (PatientAlert warning : savedWarnings()) {
                    DB.alerts().deleteById(warning.id());
                }
            }
            // Synthetic fixture-only teardown: the legacy ORMLite
            // deleteById count can be zero even for a readable row (#977).
            // Scope cleanup to this unique disposable owner and identifier
            // prefix; never issue a global clinical-table DELETE.
            DB.helper().getWritableDatabase().delete("Allergens",
                    "Patient = ? AND Identifier LIKE ?",
                    new String[]{String.valueOf(owner.getId()),
                            "ci-batch-" + suffix + "-%"});
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
    public void incompleteHighWarningDetailsDemandReviewWithoutRemovingAlert()
            throws Exception {
        try (Fixture f = new Fixture(true)) {
            AllergyPatientAlert warning = f.persistExistingWarning();
            PatientAllergen selected = f.savedAllergens().get(0);
            for (String malformed : new String[]{
                    "{}", "{\"allergens\":[]}", "{\"allergens\":[null]}"}) {
                // Deliberately inject legacy/corrupt data using raw SQLite:
                // the ordinary DAO may reject it before this recovery path
                // ever gets a chance to verify persisted warning details.
                DB.helper().getWritableDatabase().execSQL(
                        "UPDATE PatientAlerts SET Details = ? WHERE _id = ?",
                        new Object[]{malformed, warning.id()});
                assertEquals(malformed,
                        DB.alerts().findById(warning.id()).getJsonDetails());
                try {
                    AllergyAlertUtil.removeAllergyAlerts(f.context, selected);
                    fail("Missing or malformed HIGH details cannot be silently skipped");
                } catch (RuntimeException | SQLException expected) {
                    // Clinical warning is intentionally preserved for review.
                }
                PatientAlert persisted = DB.alerts().findById(warning.id());
                assertNotNull("HIGH warning must remain persisted", persisted);
                assertEquals(PatientAlert.Level.HIGH, persisted.getLevel());
                assertEquals("Corrupt details must not be overwritten",
                        malformed, persisted.getJsonDetails());
            }
        }
    }

    @Test
    public void duplicateHighWarningAllergenIdentityDemandReview()
            throws Exception {
        try (Fixture f = new Fixture(true)) {
            AllergyPatientAlert warning = f.persistExistingWarning();
            PatientAllergen selected = f.savedAllergens().get(0);
            AllergenVO duplicate = new AllergenVO(selected);
            warning.setDetails(new AllergyPatientAlert.AllergyAlertInfo(
                    Arrays.asList(duplicate, new AllergenVO(selected))));
            String duplicateDetails = warning.getJsonDetails();
            DB.helper().getWritableDatabase().execSQL(
                    "UPDATE PatientAlerts SET Details = ? WHERE _id = ?",
                    new Object[]{duplicateDetails, warning.id()});
            assertEquals(duplicateDetails,
                    DB.alerts().findById(warning.id()).getJsonDetails());
            try {
                AllergyAlertUtil.removeAllergyAlerts(f.context, selected);
                fail("Ambiguous duplicate HIGH identities need review");
            } catch (RuntimeException | SQLException expected) {
                // Never unblock a medicine using ambiguous imported data.
            }
            PatientAlert persisted = DB.alerts().findById(warning.id());
            assertNotNull(persisted);
            assertEquals(PatientAlert.Level.HIGH, persisted.getLevel());
            assertEquals(2, ((AllergyPatientAlert) persisted.map())
                    .getDetails().getAllergens().size());
        }
    }

    @Test
    public void duplicateExistingHighWarningRejectsNewAllergenAndRollsBackSql()
            throws Exception {
        try (Fixture f = new Fixture(true)) {
            AllergyPatientAlert warning = f.persistExistingWarning();
            PatientAllergen existing = f.savedAllergens().get(0);
            warning.setDetails(new AllergyPatientAlert.AllergyAlertInfo(
                    Arrays.asList(new AllergenVO(existing), new AllergenVO(existing))));
            String originalDetails = warning.getJsonDetails();
            // Seed a damaged legacy alert directly; the regular DAO edit
            // must never be weakened just to create a regression fixture.
            DB.helper().getWritableDatabase().execSQL(
                    "UPDATE PatientAlerts SET Details = ? WHERE _id = ?",
                    new Object[]{originalDetails, warning.id()});
            PatientAllergen newAllergen = f.allergen("must-rollback-duplicate-details");
            try {
                AtomicAllergenRegistration.register(f.context, f.owner,
                        Collections.singletonList(newAllergen),
                        (context, allergen) -> Collections.singletonList(f.medicine));
                fail("Duplicate persisted HIGH identities must reject registration");
            } catch (RuntimeException expected) {
                // SQL INSERT rolled back, retained HIGH warning unmodified.
            }
            assertNull(newAllergen.getId());
            assertEquals("Rejected registration leaves old allergen only",
                    1, f.savedAllergens().size());
            PatientAlert retained = DB.alerts().findById(warning.id());
            assertNotNull(retained);
            assertEquals(PatientAlert.Level.HIGH, retained.getLevel());
            assertEquals(originalDetails, retained.getJsonDetails());
        }
    }

    @Test
    public void nestedPatientAllergyCleanupCannotOutliveOuterRollback()
            throws Exception {
        try (Fixture f = new Fixture(true)) {
            AllergyPatientAlert warning = f.persistExistingWarning();
            PatientAllergen allergen = f.savedAllergens().get(0);
            try {
                DB.transaction(() -> {
                    assertTrue(DB.helper().getWritableDatabase().inTransaction());
                    AllergyAlertUtil.removeAllergyAlerts(f.context, allergen);
                    return null;
                });
                fail("Nested warning cleanup must be rejected before Android effects");
            } catch (RuntimeException expected) {
                // Cleanup may only run after the outermost transaction ends.
            }
            assertNotNull("Original HIGH warning must survive",
                    DB.alerts().findById(warning.id()));
            assertEquals(1, f.savedAllergens().size());
        }
    }

    @Test
    public void nestedMedicineAllergyCleanupCannotRemoveAHighWarning()
            throws Exception {
        try (Fixture f = new Fixture(true)) {
            AllergyPatientAlert warning = f.persistExistingWarning();
            try {
                DB.transaction(() -> {
                    assertTrue(DB.helper().getWritableDatabase().inTransaction());
                    AllergyAlertUtil.removeAllergyAlerts(f.medicine);
                    return null;
                });
                fail("Nested medicine alert cleanup must not cancel warning alarms");
            } catch (RuntimeException expected) {
                // A nested savepoint is not an independently durable COMMIT.
            }
            assertNotNull("Original HIGH warning must survive",
                    DB.alerts().findById(warning.id()));
        }
    }

    @Test
    public void persistedAllergenCannotClearHighWarningUntilCommittedDeletion()
            throws Exception {
        try (Fixture f = new Fixture(true)) {
            AllergyPatientAlert warning = f.persistExistingWarning();
            PatientAllergen registered = f.savedAllergens().get(0);
            try {
                AllergyAlertUtil.removeAllergyAlerts(f.context, registered);
                fail("An existing allergen must keep its HIGH medicine warning");
            } catch (RuntimeException | SQLException expected) {
                // Out-of-order cleanup cannot bypass a registered allergy.
            }
            assertNotNull("Rejected removal must retain the HIGH warning",
                    DB.alerts().findById(warning.id()));
            assertEquals(1, f.savedAllergens().size());

            // The intended flow commits the allergen-row deletion first and
            // only then reconciles the HIGH warning; verify both directions.
            // This fixture intentionally simulates an already-COMMITTED SQL
            // removal. Delete by the verified stable owner and unique
            // synthetic allergen identifier rather than trusting an
            // ORMLite-provided row ID from an imported/cached Java object.
            assertEquals(1, DB.helper().getWritableDatabase().delete(
                    "Allergens", "Patient = ? AND Identifier = ?",
                    new String[]{String.valueOf(f.owner.getId()),
                            registered.getIdentifier()}));
            assertTrue("Allergen removal must be committed before alert cleanup",
                    f.savedAllergens().isEmpty());
            AllergyAlertUtil.removeAllergyAlerts(f.context, registered);
            assertNull("After verified deletion the stale HIGH warning is retired",
                    DB.alerts().findById(warning.id()));
        }
    }

    @Test
    public void reRegisteredAllergenCannotBeRemovedByStaleHighCleanup()
            throws Exception {
        try (Fixture f = new Fixture(true)) {
            AllergyPatientAlert warning = f.persistExistingWarning();
            PatientAllergen deleted = f.savedAllergens().get(0);
            assertEquals(1, DB.helper().getWritableDatabase().delete(
                    "Allergens", "Patient = ? AND Identifier = ?",
                    new String[]{String.valueOf(f.owner.getId()),
                            deleted.getIdentifier()}));
            assertTrue(f.savedAllergens().isEmpty());

            // A second UI/provider action wins the SQLite lock and restores
            // the SAME clinical allergen before an old post-COMMIT callback
            // can retire the stale HIGH warning.
            PatientAllergen reRegistered = f.allergen("previous");
            assertEquals(1, DB.patientAllergens().create(reRegistered));
            assertEquals(1, f.savedAllergens().size());
            try {
                AlertManager.removeAlertAfterAllergenDeletion(
                        f.savedWarnings().get(0), deleted);
                fail("A re-registered allergy must keep its HIGH warning");
            } catch (RuntimeException expected) {
                // Recheck and alert DELETE are one SQL unit; no unblock occurs.
            }
            PatientAlert retained = DB.alerts().findById(warning.id());
            assertNotNull("Stale cleanup must not erase an active HIGH warning", retained);
            assertEquals(PatientAlert.Level.HIGH, retained.getLevel());
            assertEquals(1, f.savedAllergens().size());
        }
    }

    @Test
    public void postCommitPlatformEffectsRejectAChangedActivePatient()
            throws Exception {
        try (Fixture f = new Fixture(true)) {
            // The selected owner is valid when the SQL operation begins.
            AtomicAllergenRegistration.requireCurrentOwnerForPlatformEffects(
                    f.context, f.owner);
            AtomicMedicineAllergyEditor.requireCurrentOwnerForPlatformEffects(
                    f.context, f.owner);
            Patient another = new Patient();
            another.setCode("ci-postcommit-switched-" + f.suffix);
            another.setName("Synthetic switched profile");
            try {
                assertEquals(1, DB.patients().create(another));
                DB.patients().setActive(another);
                try {
                    AtomicAllergenRegistration.requireCurrentOwnerForPlatformEffects(
                            f.context, f.owner);
                    fail("An old owner cannot dispatch platform effects after a switch");
                } catch (IllegalStateException expected) {
                    // The caller reports committed-but-recovery-incomplete,
                    // not a database rollback or a successful alarm update.
                }
                try {
                    AtomicMedicineAllergyEditor.requireCurrentOwnerForPlatformEffects(
                            f.context, f.owner);
                    fail("An old medicine editor cannot dispatch events after a switch");
                } catch (IllegalStateException expected) {
                    // Persisted medicine remains committed, but no stale
                    // owner event may be published to the active profile.
                }
                assertTrue(f.savedAllergens().isEmpty());
                assertTrue(f.savedWarnings().isEmpty());
            } finally {
                DB.patients().setActive(f.owner);
                if (another.getId() != null
                        && DB.patients().findById(another.getId()) != null) {
                    DB.patients().deleteById(another.getId());
                }
            }
        }
    }

    @Test
    public void nestedMedicineCreateIsRejectedBeforePersistingClinicalRows()
            throws Exception {
        try (Fixture f = new Fixture(true)) {
            Medicine proposed = new Medicine(
                    "Synthetic nested create", Presentation.PILLS);
            proposed.setPatient(f.owner);
            DB.transaction(() -> {
                assertTrue(DB.helper().getWritableDatabase().inTransaction());
                try {
                    AtomicMedicineAllergyEditor.create(f.context, proposed, null);
                    fail("Medicine creation must reject an enclosing SQL transaction");
                } catch (IllegalStateException expected) {
                    assertTrue(expected.getMessage().contains("outermost SQLite"));
                }
                return null;
            });
            assertNull("Rejected creation cannot leave a generated medicine ID",
                    proposed.getId());
            assertTrue("Rejected creation cannot insert allergy alerts",
                    f.savedWarnings().isEmpty());
            assertTrue(f.savedAllergens().isEmpty());
        }
    }

    @Test
    public void nestedMedicineEditIsRejectedBeforeUpdatingClinicalRows()
            throws Exception {
        try (Fixture f = new Fixture(true)) {
            Medicine proposed = new Medicine(
                    "Synthetic forbidden nested edit", Presentation.PILLS);
            proposed.setId(f.medicine.getId());
            proposed.setPatient(f.owner);
            DB.transaction(() -> {
                assertTrue(DB.helper().getWritableDatabase().inTransaction());
                try {
                    AtomicMedicineAllergyEditor.edit(f.context, proposed, null);
                    fail("Medicine edit must reject an enclosing SQL transaction");
                } catch (IllegalStateException expected) {
                    assertTrue(expected.getMessage().contains("outermost SQLite"));
                }
                return null;
            });
            Medicine persisted = new Medicine();
            persisted.setId(f.medicine.getId());
            assertEquals(1, DB.medicines().refresh(persisted));
            assertEquals("Synthetic batch medicine", persisted.getName());
            assertTrue("Rejected edit cannot create or remove allergy alerts",
                    f.savedWarnings().isEmpty());
        }
    }

    @Test
    public void verifiedAllergenDeletionRemovesExactlyOneScopedSqliteRow()
            throws Exception {
        // #977 reproduced legacy ORMLite deleteById returning zero on
        // API23/33/37 for a valid generated ID. Exercise the production
        // owner-scoped replacement under the SAME SQLite transaction.
        try (Fixture f = new Fixture(false)) {
            PatientAllergen proposed = f.allergen("dao-by-id");
            assertEquals(1, DB.patientAllergens().create(proposed));
            assertNotNull("Created allergen must acquire a row ID", proposed.getId());
            assertTrue(proposed.getId() > 0L);
            List<PatientAllergen> readBack = f.savedAllergens();
            assertEquals(1, readBack.size());
            assertEquals("DAO must restore the generated row ID",
                    proposed.getId(), readBack.get(0).getId());
            assertNotNull(DB.patientAllergens().findById(readBack.get(0).getId()));
            DB.transaction(() -> {
                assertEquals(1, DB.patientAllergens().deleteVerifiedOwned(
                        readBack.get(0), f.owner));
                return null;
            });
            assertTrue("No synthetic allergen may remain after successful COMMIT",
                    f.savedAllergens().isEmpty());
        }
    }

    @Test
    public void verifiedDeletionRejectsRecycledOwnerAndPreservesRows()
            throws Exception {
        try (Fixture f = new Fixture(false)) {
            PatientAllergen proposed = f.allergen("owner-guard");
            assertEquals(1, DB.patientAllergens().create(proposed));
            Patient forged = new Patient();
            forged.setId(f.owner.getId());
            forged.setCode("ci-recycled-owner-" + f.suffix);
            try {
                DB.transaction(() -> {
                    DB.patientAllergens().deleteVerifiedOwned(proposed, forged);
                    return null;
                });
                fail("Same SQL row ID with different patient code must be rejected");
            } catch (RuntimeException expected) {
                // A stale caller must not erase the new owner's allergen.
            }
            assertEquals(1, f.savedAllergens().size());
            assertNotNull(DB.patientAllergens().findById(proposed.getId()));
        }
    }

    @Test
    public void verifiedAllergenGroupDeleteRollsBackEarlierSqlRowOnLaterFailure()
            throws Exception {
        try (Fixture f = new Fixture(false)) {
            PatientAllergen first = f.allergen("rollback-first");
            PatientAllergen second = f.allergen("rollback-second");
            assertEquals(1, DB.patientAllergens().create(first));
            assertEquals(1, DB.patientAllergens().create(second));
            assertEquals(2, f.savedAllergens().size());
            PatientAllergen forgedSecond = new PatientAllergen(
                    "Synthetic forged later row", second.getType(),
                    "ci-unverified-identifier", f.owner);
            forgedSecond.setId(second.getId());
            try {
                DB.transaction(() -> {
                    assertEquals(1, DB.patientAllergens().deleteVerifiedOwned(
                            first, f.owner));
                    // This second preflight must abort the entire unit,
                    // including SQLiteDatabase.delete() of the FIRST row.
                    DB.patientAllergens().deleteVerifiedOwned(forgedSecond, f.owner);
                    return null;
                });
                fail("A stale second allergen must roll back the whole SQL group");
            } catch (RuntimeException expected) {
                // No HIGH warning is retired and no partial removal commits.
            }
            assertNotNull("First row must survive outer transaction rollback",
                    DB.patientAllergens().findById(first.getId()));
            assertNotNull("Second row must remain unchanged",
                    DB.patientAllergens().findById(second.getId()));
            assertEquals(2, f.savedAllergens().size());
        }
    }

    @Test
    public void verifiedAllergenDeleteCannotTouchOtherPatientsSameAllergen()
            throws Exception {
        try (Fixture f = new Fixture(false)) {
            Patient other = new Patient();
            other.setCode("ci-independent-owner-" + f.suffix);
            other.setName("Synthetic other owner");
            assertEquals(1, DB.patients().create(other));
            PatientAllergen first = f.allergen("shared-clinical-identifier");
            PatientAllergen second = new PatientAllergen(
                    first.getName(), first.getType(), first.getIdentifier(), other);
            try {
                assertEquals(1, DB.patientAllergens().create(first));
                assertEquals(1, DB.patientAllergens().create(second));
                DB.transaction(() -> {
                    DB.patientAllergens().deleteVerifiedOwned(first, f.owner);
                    return null;
                });
                assertTrue(f.savedAllergens().isEmpty());
                assertNotNull("Another patient's same catalog allergen must survive",
                        DB.patientAllergens().findById(second.getId()));
                assertEquals(other.getId(),
                        DB.patientAllergens().findById(second.getId())
                                .getPatient().getId());
            } finally {
                // Only synthetic, explicitly identified second-owner rows.
                DB.helper().getWritableDatabase().delete("Allergens",
                        "_id = ? AND Patient = ? AND Identifier = ?",
                        new String[]{String.valueOf(second.getId()),
                                String.valueOf(other.getId()),
                                second.getIdentifier()});
                DB.patients().deleteById(other.getId());
            }
        }
    }

    @Test
    public void verifiedDeletionCannotRunWithoutOutermostSqlTransaction()
            throws Exception {
        try (Fixture f = new Fixture(false)) {
            PatientAllergen proposed = f.allergen("requires-transaction");
            assertEquals(1, DB.patientAllergens().create(proposed));
            try {
                DB.patientAllergens().deleteVerifiedOwned(proposed, f.owner);
                fail("No uncommitted SQL batch may remove a clinical allergen");
            } catch (IllegalStateException expected) {
                // Caller must own a transaction for atomic group deletion.
            }
            assertEquals(1, f.savedAllergens().size());
        }
    }

    @Test
    public void patientScopedAllergenDaoReadRejectsStaleRecycledIdentity()
            throws Exception {
        try (Fixture f = new Fixture(false)) {
            PatientAllergen verified = f.allergen("patient-scoped-read");
            assertEquals(1, DB.patientAllergens().create(verified));
            assertEquals(1, DB.patientAllergens().findAll(f.owner).size());

            // Simulate a stale profile snapshot after database restore. The
            // numeric owner ID matches, but its durable code does not.
            Patient recycledSnapshot = new Patient();
            recycledSnapshot.setId(f.owner.getId());
            recycledSnapshot.setCode("ci-replaced-patient-" + f.suffix);
            assertTrue("A recycled row ID must not reveal another patient's allergens",
                    DB.patientAllergens().findAll(recycledSnapshot).isEmpty());

            Patient incompleteSnapshot = new Patient();
            incompleteSnapshot.setId(f.owner.getId());
            assertTrue("Unidentified legacy snapshots must not disclose allergens",
                    DB.patientAllergens().findAll(incompleteSnapshot).isEmpty());
            assertTrue(DB.patientAllergens().findAll((Patient) null).isEmpty());

            assertEquals("Denied reads must not delete or alter real rows",
                    1, f.savedAllergens().size());
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
