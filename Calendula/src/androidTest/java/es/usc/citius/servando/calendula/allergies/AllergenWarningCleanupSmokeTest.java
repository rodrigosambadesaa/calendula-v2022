/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.allergies;

import android.content.Context;
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

            // Simulate a restored patient that has inherited precisely the
            // same numeric SQLite ID but NOT the same stable local code.
            // Neither the original HIGH warning nor its details may change.
            Patient recycled = new Patient();
            recycled.setId(owner.getId());
            recycled.setCode("ci-different-restored-owner-" + seed);
            PatientAllergen staleRemoval = new PatientAllergen(a, recycled);
            try {
                AllergyAlertUtil.removeAllergyAlerts(context, staleRemoval);
                fail("Recycled SQLite patient ID authorized stale allergen cleanup");
            } catch (IllegalStateException expected) {
                // The preflight must reject before mutating any warning row.
            }
            assertNotNull(DB.alerts().findById(firstAlert.id()));
            AllergyPatientAlert preserved = (AllergyPatientAlert)
                    DB.alerts().findById(secondAlert.id()).map();
            assertEquals(2, preserved.getDetails().getAllergens().size());

            Patient ownerWithoutCode = new Patient();
            ownerWithoutCode.setId(owner.getId());
            try {
                AllergyAlertUtil.removeAllergyAlerts(context,
                        new PatientAllergen(a, ownerWithoutCode));
                fail("Ownerless restored patient unexpectedly authorized warning cleanup");
            } catch (IllegalArgumentException expected) {
                // A legacy unowned profile requires identity migration.
            }
            assertNotNull(DB.alerts().findById(firstAlert.id()));
            assertEquals(2, ((AllergyPatientAlert)
                    DB.alerts().findById(secondAlert.id()).map())
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
