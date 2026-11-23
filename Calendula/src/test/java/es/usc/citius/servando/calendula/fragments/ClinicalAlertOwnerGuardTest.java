/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.persistence.Patient;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Pure identity checks, without access to Android or patients' SQLite data. */
// Patient initializes android.graphics.Color; run its constructors with Android shadows.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ClinicalAlertOwnerGuardTest {
    private static Patient patient(Long id, String code) {
        Patient p = new Patient();
        p.setId(id);
        p.setCode(code);
        return p;
    }

    @Test
    public void stableOwnerMustMatchOpenedMedicineActiveAndPersistedPatient() {
        Patient original = patient(19L, "synthetic-original");
        Patient sameAfterReload = patient(19L, "synthetic-original");
        Patient recycled = patient(19L, "synthetic-replacement");
        assertTrue(ClinicalAlertOwnerGuard.isCurrentStable(
                8L, original, 8L, sameAfterReload, sameAfterReload, sameAfterReload));
        assertFalse("Current medicine belongs to different restored owner",
                ClinicalAlertOwnerGuard.isCurrentStable(
                        8L, original, 8L, recycled, original, original));
        assertFalse("Selected patient changed but recycled row ID",
                ClinicalAlertOwnerGuard.isCurrentStable(
                        8L, original, 8L, original, recycled, original));
        assertFalse("SQLite row was replaced with different account code",
                ClinicalAlertOwnerGuard.isCurrentStable(
                        8L, original, 8L, original, original, recycled));
        assertFalse("Missing persisted owner cannot reveal clinical alerts",
                ClinicalAlertOwnerGuard.isCurrentStable(
                        8L, original, 8L, original, original, null));
        assertFalse(ClinicalAlertOwnerGuard.isCurrentStable(
                8L, patient(19L, ""), 8L, original, original, original));
        assertFalse(ClinicalAlertOwnerGuard.isCurrentStable(
                8L, original, 9L, original, original, original));
    }

    @Test
    public void selectedMedicineMayOnlyShowCurrentPatientAlerts() {
        assertTrue(ClinicalAlertOwnerGuard.isCurrent(8L, 19L, 8L, 19L, 19L));
        assertFalse(ClinicalAlertOwnerGuard.isCurrent(8L, 19L, 8L, 19L, 20L));
        assertFalse(ClinicalAlertOwnerGuard.isCurrent(8L, 19L, 8L, 20L, 19L));
        assertFalse(ClinicalAlertOwnerGuard.isCurrent(8L, 19L, 9L, 19L, 19L));
    }

    @Test
    public void missingOrUnsavedRecordsNeverAuthorizeClinicalAlertDisplay() {
        assertFalse(ClinicalAlertOwnerGuard.isCurrent(null, 19L, 8L, 19L, 19L));
        assertFalse(ClinicalAlertOwnerGuard.isCurrent(8L, null, 8L, 19L, 19L));
        assertFalse(ClinicalAlertOwnerGuard.isCurrent(8L, 19L, null, 19L, 19L));
        assertFalse(ClinicalAlertOwnerGuard.isCurrent(8L, 19L, 8L, null, 19L));
        assertFalse(ClinicalAlertOwnerGuard.isCurrent(8L, 19L, 8L, 19L, null));
        assertFalse(ClinicalAlertOwnerGuard.isCurrent(0L, 19L, 0L, 19L, 19L));
        assertFalse(ClinicalAlertOwnerGuard.isCurrent(8L, -1L, 8L, -1L, -1L));
    }
}
