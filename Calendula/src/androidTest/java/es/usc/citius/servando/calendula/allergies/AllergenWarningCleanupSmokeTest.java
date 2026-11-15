/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.allergies;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Arrays;
import java.util.Collections;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.PatientAlert;
import es.usc.citius.servando.calendula.persistence.PatientAllergen;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.persistence.alerts.AllergyPatientAlert;

import static org.junit.Assert.*;

/** SQLite fixtures are unique synthetic medicines; no database reset. */
@RunWith(AndroidJUnit4.class)
public class AllergenWarningCleanupSmokeTest {
    @Test
    public void allergyDetailsUpdateChecksStableOwnerAndRollsBackSqlWriteFailure()
            throws Exception {
        assertTrue(DB.initialized);
        String seed = Long.toString(System.nanoTime());
        Patient owner = new Patient();
        owner.setCode("ci-allergy-atomic-owner-" + seed);
        owner.setName("Synthetic alert update owner");
        Medicine medicine = new Medicine("Synthetic immutable-warning fixture",
                Presentation.PILLS);
        AllergenVO first = new AllergenVO(
                AllergenType.EXCIPIENT, "Synthetic original", "CI-ORIG-" + seed);
        AllergenVO replacement = new AllergenVO(
                AllergenType.EXCIPIENT, "Synthetic replacement", "CI-NEW-" + seed);
        AllergyPatientAlert saved = null;
        final String trigger = "ci_reject_allergy_warning_detail_update";
        final SQLiteDatabase sqlite = DB.helper().getWritableDatabase();
        sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
        try {
            assertEquals(1, DB.patients().create(owner));
            medicine.setPatient(owner);
            assertEquals(1, DB.medicines().create(medicine));
            saved = new AllergyPatientAlert(medicine, Collections.singletonList(first));
            assertEquals(1, DB.alerts().create(saved));
            assertNotNull(saved.id());
            final Long alertId = saved.id();
            final String original = DB.alerts().findById(alertId).getJsonDetails();

            AllergyPatientAlert revised =
                    (AllergyPatientAlert) DB.alerts().findById(alertId).map();
            revised.setDetails(new AllergyPatientAlert.AllergyAlertInfo(
                    Collections.singletonList(replacement)));
            Patient forged = new Patient();
            forged.setId(owner.getId());
            forged.setCode("ci-stale-recycled-owner-" + seed);
            revised.setPatient(forged);
            boolean rejected = false;
            try {
                AllergyAlertUtil.updateExistingAllergyWarning(revised);
            } catch (Exception expected) {
                rejected = true;
            }
            assertTrue("A recycled owner code cannot change a HIGH warning", rejected);
            assertEquals("Rejected identity must preserve original warning",
                    original, DB.alerts().findById(alertId).getJsonDetails());

            revised.setPatient(owner);
            sqlite.execSQL("CREATE TRIGGER " + trigger
                    + " BEFORE UPDATE ON PatientAlerts WHEN OLD._id = " + alertId
                    + " BEGIN SELECT RAISE(ABORT, 'synthetic allergy update failure'); END;");
            rejected = false;
            try {
                AllergyAlertUtil.updateExistingAllergyWarning(revised);
            } catch (Exception expected) {
                rejected = true;
            }
            assertTrue("SQLite write failure must be propagated, not silently accepted",
                    rejected);
            assertEquals("Failed SQL update must not alter the original warning",
                    original, DB.alerts().findById(alertId).getJsonDetails());

            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            AllergyAlertUtil.updateExistingAllergyWarning(revised);
            assertEquals("Successful retry must update the actual stored warning",
                    revised.getJsonDetails(),
                    DB.alerts().findById(alertId).getJsonDetails());
        } finally {
            sqlite.execSQL("DROP TRIGGER IF EXISTS " + trigger);
            if (saved != null && saved.id() != null
                    && DB.alerts().findById(saved.id()) != null) {
                DB.alerts().deleteById(saved.id());
            }
            if (medicine.getId() != null
                    && DB.medicines().findById(medicine.getId()) != null) {
                DB.medicines().deleteById(medicine.getId());
            }
            if (owner.getId() != null
                    && DB.patients().findById(owner.getId()) != null) {
                DB.patients().deleteById(owner.getId());
            }
        }
    }

    @Test
    public void removingAllergenDeletesEmptyWarningButPreservesOtherWarningIngredients()
            throws Exception {
        assertTrue(DB.initialized);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String seed = Long.toString(System.nanoTime());
        Patient owner = new Patient();
        owner.setCode("ci-allergen-cleanup-" + seed);
        owner.setName("Synthetic allergen owner");
        Medicine onlyA = new Medicine("Synthetic one-allergen medicine", Presentation.PILLS);
        Medicine alsoB = new Medicine("Synthetic two-allergen medicine", Presentation.PILLS);
        AllergenVO a = new AllergenVO(AllergenType.EXCIPIENT, "Synthetic alpha", "CI-A-" + seed);
        AllergenVO b = new AllergenVO(AllergenType.EXCIPIENT, "Synthetic beta", "CI-B-" + seed);
        AllergyPatientAlert firstAlert = null;
        AllergyPatientAlert secondAlert = null;
        try {
            assertEquals(1, DB.patients().create(owner));
            onlyA.setPatient(owner);
            alsoB.setPatient(owner);
            assertEquals(1, DB.medicines().create(onlyA));
            assertEquals(1, DB.medicines().create(alsoB));
            firstAlert = new AllergyPatientAlert(onlyA, Collections.singletonList(a));
            secondAlert = new AllergyPatientAlert(alsoB, Arrays.asList(a, b));
            assertEquals(1, DB.alerts().create(firstAlert));
            assertEquals(1, DB.alerts().create(secondAlert));

            // Simulate an old queued profile callback after a database
            // restore has recycled this numeric patient ID for another code.
            // Neither the first HIGH warning nor the multi-allergen warning
            // may be mutated by that stale callback.
            Patient recycledId = new Patient();
            recycledId.setId(owner.getId());
            recycledId.setCode("ci-other-patient-after-restore-" + seed);
            PatientAllergen forgedRemoval = new PatientAllergen(a, recycledId);
            boolean rejected = false;
            try {
                AllergyAlertUtil.removeAllergyAlerts(context, forgedRemoval);
            } catch (RuntimeException | java.sql.SQLException expected) {
                rejected = true;
            }
            assertTrue("Same-ID/different-code allergen cleanup must fail closed", rejected);
            assertNotNull("Rejected callback must preserve first HIGH alert",
                    DB.alerts().findById(firstAlert.id()));
            assertEquals("Rejected callback must preserve second warning details",
                    2, ((AllergyPatientAlert) DB.alerts().findById(secondAlert.id()).map())
                            .getDetails().getAllergens().size());

            PatientAllergen removed = new PatientAllergen(a, owner);
            AllergyAlertUtil.removeAllergyAlerts(context, removed);

            assertNull("HIGH warning only about removed allergen must be removed",
                    DB.alerts().findById(firstAlert.id()));
            PatientAlert retained = DB.alerts().findById(secondAlert.id());
            assertNotNull("Other active allergen must retain its HIGH warning", retained);
            AllergyPatientAlert typed = (AllergyPatientAlert) retained.map();
            assertNotNull(typed.getDetails());
            assertEquals(1, typed.getDetails().getAllergens().size());
            assertEquals(b, typed.getDetails().getAllergens().get(0));
        } finally {
            for (AllergyPatientAlert alert : new AllergyPatientAlert[]{firstAlert, secondAlert}) {
                if (alert != null && alert.id() != null && DB.alerts().findById(alert.id()) != null)
                    DB.alerts().deleteById(alert.id());
            }
            for (Medicine medicine : new Medicine[]{onlyA, alsoB}) {
                if (medicine.getId() != null && DB.medicines().findById(medicine.getId()) != null)
                    DB.medicines().deleteById(medicine.getId());
            }
            if (owner.getId() != null && DB.patients().findById(owner.getId()) != null)
                DB.patients().deleteById(owner.getId());
        }
    }
}
