/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util.alerts;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

import es.usc.citius.servando.calendula.allergies.AllergenType;
import es.usc.citius.servando.calendula.allergies.AllergenVO;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.PatientAlert;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

import static org.junit.Assert.*;

/**
 * Disposable regression for #971: a failure inserting a HIGH allergy warning
 * must roll back the preceding medicine INSERT. The same draft is retryable,
 * and neither a failed write nor a stale selected owner may touch patient B.
 */
@RunWith(AndroidJUnit4.class)
public class AtomicMedicineCreationRollbackSmokeTest {
    @Test
    public void alertInsertRollbackPreservesForeignOwnerAndAllowsExactDraftRetry()
            throws Exception {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Patient previouslyActive = DB.patients().getActive(context);
        String suffix = Long.toString(System.nanoTime());
        String name = "Synthetic atomic creation " + suffix;

        Patient owner = new Patient();
        owner.setCode("ci-atomic-create-a-" + suffix);
        owner.setName("Synthetic creation owner");
        Patient foreign = new Patient();
        foreign.setCode("ci-atomic-create-b-" + suffix);
        foreign.setName("Synthetic unrelated owner");

        Medicine candidate = new Medicine(name, Presentation.PILLS);
        Medicine otherMedicine = new Medicine("Synthetic existing other " + suffix,
                Presentation.PILLS);
        SQLiteDatabase sqlite = DB.helper().getWritableDatabase();
        final String trigger = "ci_atomic_create_alert_abort";
        sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
        try {
            assertEquals(1, DB.patients().create(owner));
            assertEquals(1, DB.patients().create(foreign));
            DB.patients().setActive(owner);
            candidate.setPatient(owner);
            otherMedicine.setPatient(foreign);
            assertEquals(1, DB.medicines().create(otherMedicine));
            Long foreignMedicineId = otherMedicine.getId();
            assertNotNull(foreignMedicineId);

            // SQLite first writes Medicines, then PatientAlerts in the same
            // outer transaction. Fail the SECOND write deterministically.
            sqlite.execSQL("CREATE TRIGGER " + trigger
                    + " BEFORE INSERT ON PatientAlerts WHEN NEW.Patient = "
                    + owner.getId()
                    + " BEGIN SELECT RAISE(ABORT, 'synthetic alert failure'); END;");
            boolean rejected = false;
            try {
                AtomicMedicineAllergyEditor.create(context, candidate,
                        Collections.singletonList(new AllergenVO(
                                AllergenType.EXCIPIENT, "Synthetic excipient",
                                "ci-create-excipient-" + suffix)));
            } catch (RuntimeException expected) {
                rejected = true;
            }
            assertTrue("Failed allergy alert INSERT must roll back creation", rejected);
            assertNull("A rolled-back generated medicine ID must be cleared",
                    candidate.getId());
            assertTrue("No orphaned medicine may survive the rollback",
                    DB.medicines().findBy(Medicine.COLUMN_NAME, name).isEmpty());
            assertTrue("No HIGH alert may survive the rollback",
                    DB.alerts().findBy(PatientAlert.COLUMN_PATIENT, owner.getId()).isEmpty());
            assertNotNull("The unrelated owner's medicine must remain",
                    DB.medicines().findById(foreignMedicineId));

            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);

            // Same original draft, but a different active patient: reject
            // rather than silently binding the medicine to the new selection.
            DB.patients().setActive(foreign);
            rejected = false;
            try {
                AtomicMedicineAllergyEditor.create(context, candidate,
                        Collections.singletonList(new AllergenVO(
                                AllergenType.EXCIPIENT, "Synthetic excipient",
                                "ci-create-excipient-" + suffix)));
            } catch (RuntimeException expected) {
                rejected = true;
            }
            assertTrue("A stale editor cannot create for an unselected owner", rejected);
            assertNull(candidate.getId());
            assertTrue(DB.medicines().findBy(Medicine.COLUMN_NAME, name).isEmpty());

            // Restore the intended selected patient and retry the EXACT draft.
            DB.patients().setActive(owner);
            AtomicMedicineAllergyEditor.create(context, candidate,
                    Collections.singletonList(new AllergenVO(
                            AllergenType.EXCIPIENT, "Synthetic excipient",
                            "ci-create-excipient-" + suffix)));
            assertNotNull("The successful retry must persist a medicine ID",
                    candidate.getId());
            assertNotNull(DB.medicines().findById(candidate.getId()));
            assertEquals("The retry must create exactly one medicine",
                    1, DB.medicines().findBy(Medicine.COLUMN_NAME, name).size());
            assertEquals("The retry must commit the matching HIGH warning",
                    1, DB.alerts().findByMedicineAndLevel(
                            candidate, PatientAlert.Level.HIGH).size());
            assertNotNull("The foreign medicine is unchanged by successful retry",
                    DB.medicines().findById(foreignMedicineId));
        } finally {
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            if (candidate.getId() != null) {
                for (PatientAlert alert : DB.alerts().findBy(
                        PatientAlert.COLUMN_MEDICINE, candidate.getId())) {
                    DB.alerts().deleteById(alert.id());
                }
                if (DB.medicines().findById(candidate.getId()) != null) {
                    DB.medicines().deleteById(candidate.getId());
                }
            }
            if (otherMedicine.getId() != null
                    && DB.medicines().findById(otherMedicine.getId()) != null) {
                DB.medicines().deleteById(otherMedicine.getId());
            }
            if (previouslyActive != null && previouslyActive.getId() != null
                    && DB.patients().findById(previouslyActive.getId()) != null) {
                DB.patients().setActive(previouslyActive);
            } else {
                PreferenceUtils.edit()
                        .remove(PreferenceKeys.PATIENTS_ACTIVE.key())
                        .remove(PreferenceKeys.PATIENTS_ACTIVE_OWNER.key())
                        .remove(PreferenceKeys.PATIENTS_RESELECTION_REQUIRED.key())
                        .commit();
            }
            if (owner.getId() != null && DB.patients().findById(owner.getId()) != null) {
                DB.patients().deleteById(owner.getId());
            }
            if (foreign.getId() != null && DB.patients().findById(foreign.getId()) != null) {
                DB.patients().deleteById(foreign.getId());
            }
        }
    }
}
