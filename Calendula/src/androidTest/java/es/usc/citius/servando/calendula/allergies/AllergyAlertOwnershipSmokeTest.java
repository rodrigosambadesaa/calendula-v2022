/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.allergies;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.persistence.alerts.AllergyPatientAlert;

import static org.junit.Assert.*;

/** Synthetic records; avoids altering any real patient medication. */
@RunWith(AndroidJUnit4.class)
public class AllergyAlertOwnershipSmokeTest {

    @Test
    public void allergyAlertsAreQueriedByOwnerAndRemovedUsingClinicalAlertManager()
            throws Exception {
        assertTrue(DB.initialized);
        Patient first = patient("owner");
        Patient other = patient("other");
        Medicine med = new Medicine("Synthetic allergy medicine", Presentation.PILLS);
        AllergyPatientAlert alert = null;
        try {
            assertEquals(1, DB.patients().create(first));
            assertEquals(1, DB.patients().create(other));
            med.setPatient(first);
            assertEquals(1, DB.medicines().create(med));
            alert = new AllergyPatientAlert(med, Collections.emptyList());
            assertEquals(1, DB.alerts().create(alert));
            assertEquals(1, AllergyAlertUtil.getAlertsForMedicine(med).size());

            Medicine forged = new Medicine("Wrong patient", Presentation.PILLS);
            forged.setId(med.getId());
            forged.setPatient(other);
            boolean rejected = false;
            try {
                AllergyAlertUtil.getAlertsForMedicine(forged);
            } catch (IllegalStateException expected) {
                rejected = true;
            }
            assertTrue("A foreign medicine ID must not disclose allergy alerts", rejected);
            assertNotNull(DB.alerts().findById(alert.id()));

            AllergyAlertUtil.removeAllergyAlerts(med);
            assertNull("Verified allergy warning must be removed", DB.alerts().findById(alert.id()));
            assertTrue(AllergyAlertUtil.getAlertsForMedicine(med).isEmpty());
        } finally {
            if (alert != null && alert.id() != null && DB.alerts().findById(alert.id()) != null)
                DB.alerts().deleteById(alert.id());
            if (med.getId() != null && DB.medicines().findById(med.getId()) != null)
                DB.medicines().deleteById(med.getId());
            if (other.getId() != null && DB.patients().findById(other.getId()) != null)
                DB.patients().deleteById(other.getId());
            if (first.getId() != null && DB.patients().findById(first.getId()) != null)
                DB.patients().deleteById(first.getId());
        }
    }

    @Test
    public void crossPatientCorruptAllergyRowIsNeverReturnedOrDeleted() throws Exception {
        assertTrue(DB.initialized);
        Patient first = patient("corrupt-owner");
        Patient other = patient("corrupt-foreign");
        Medicine med = new Medicine("Synthetic corrupted allergy", Presentation.PILLS);
        AllergyPatientAlert alert = null;
        try {
            assertEquals(1, DB.patients().create(first));
            assertEquals(1, DB.patients().create(other));
            med.setPatient(first);
            assertEquals(1, DB.medicines().create(med));
            alert = new AllergyPatientAlert(med, Collections.emptyList());
            assertEquals(1, DB.alerts().create(alert));
            DB.helper().getWritableDatabase().execSQL(
                "UPDATE PatientAlerts SET Patient = ? WHERE _id = ?",
                new Object[]{other.getId(), alert.id()});

            boolean readRejected = false;
            try {
                AllergyAlertUtil.getAlertsForMedicine(med);
            } catch (IllegalStateException expected) {
                readRejected = true;
            }
            assertTrue("Corrupt clinical alerts cannot be returned", readRejected);
            boolean deletionRejected = false;
            try {
                AllergyAlertUtil.removeAllergyAlerts(med);
            } catch (IllegalStateException expected) {
                deletionRejected = true;
            }
            assertTrue("Corrupt clinical alerts cannot be deleted", deletionRejected);
            assertNotNull(DB.alerts().findById(alert.id()));
        } finally {
            if (alert != null && alert.id() != null && DB.alerts().findById(alert.id()) != null)
                DB.alerts().deleteById(alert.id());
            if (med.getId() != null && DB.medicines().findById(med.getId()) != null)
                DB.medicines().deleteById(med.getId());
            if (other.getId() != null && DB.patients().findById(other.getId()) != null)
                DB.patients().deleteById(other.getId());
            if (first.getId() != null && DB.patients().findById(first.getId()) != null)
                DB.patients().deleteById(first.getId());
        }
    }

    private static Patient patient(String name) {
        Patient p = new Patient();
        p.setCode("ci-allergy-" + name + "-" + System.nanoTime());
        p.setName("Synthetic " + name);
        return p;
    }
}
