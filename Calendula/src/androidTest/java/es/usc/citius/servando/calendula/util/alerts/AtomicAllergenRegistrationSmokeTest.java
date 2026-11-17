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
