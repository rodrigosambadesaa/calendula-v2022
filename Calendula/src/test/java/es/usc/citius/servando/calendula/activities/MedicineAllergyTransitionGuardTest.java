/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The cleanup decision must not depend on the *new* prescription being linked. */
public class MedicineAllergyTransitionGuardTest {
    @Test
    public void unchangedPrescriptionKeepsItsActiveWarning() {
        assertFalse(MedicineAllergyTransitionGuard.prescriptionChanged(
                "111", "aemps", "111", "aemps"));
        assertFalse(MedicineAllergyTransitionGuard.prescriptionChanged(
                null, null, null, null));
    }

    @Test
    public void changedCodeOrDrugDatabaseInvalidatesOldWarning() {
        assertTrue(MedicineAllergyTransitionGuard.prescriptionChanged(
                "111", "aemps", "222", "aemps"));
        assertTrue(MedicineAllergyTransitionGuard.prescriptionChanged(
                "111", "aemps", "111", "other-database"));
    }

    @Test
    public void unlinkAndNewLinkAreHandledWithoutNullPointerExceptions() {
        assertTrue(MedicineAllergyTransitionGuard.prescriptionChanged(
                "111", "aemps", null, null));
        assertTrue(MedicineAllergyTransitionGuard.prescriptionChanged(
                "111", "aemps", null, "aemps"));
        assertTrue(MedicineAllergyTransitionGuard.prescriptionChanged(
                null, null, "111", "aemps"));
    }
}
