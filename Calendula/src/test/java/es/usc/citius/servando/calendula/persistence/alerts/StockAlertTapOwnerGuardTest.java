/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.persistence.alerts;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Presentation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** No Android activity launches or real patient records. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class StockAlertTapOwnerGuardTest {
    private static Patient owner(Long id, String code) {
        Patient patient = new Patient();
        patient.setId(id);
        patient.setCode(code);
        return patient;
    }

    private static Medicine medicine(Patient patient) {
        Medicine result = new Medicine("Synthetic stock medicine", Presentation.PILLS);
        result.setId(1001L);
        result.setPatient(patient);
        return result;
    }

    @Test
    public void validSamePatientMayOpenStockEditor() {
        Patient captured = owner(51L, "synthetic-registered-patient");
        assertTrue(StockRunningOutAlert.StockAlertViewProvider.mayLaunchStockEditor(
                captured, owner(51L, "synthetic-registered-patient"),
                medicine(owner(51L, "synthetic-registered-patient"))));
    }

    @Test
    public void switchingProfileAfterRenderingRevokesButtonAuthority() {
        Patient captured = owner(51L, "synthetic-registered-patient");
        Medicine valid = medicine(owner(51L, "synthetic-registered-patient"));
        assertFalse(StockRunningOutAlert.StockAlertViewProvider.mayLaunchStockEditor(
                captured, owner(51L, "different-patient-same-row-id"), valid));
        assertFalse(StockRunningOutAlert.StockAlertViewProvider.mayLaunchStockEditor(
                captured, owner(52L, "synthetic-registered-patient"), valid));
        assertFalse(StockRunningOutAlert.StockAlertViewProvider.mayLaunchStockEditor(
                captured, null, valid));
    }

    @Test
    public void replacementMedicineOrLegacyOwnerCannotBeOpened() {
        Patient captured = owner(51L, "synthetic-registered-patient");
        Patient selected = owner(51L, "synthetic-registered-patient");
        assertFalse(StockRunningOutAlert.StockAlertViewProvider.mayLaunchStockEditor(
                captured, selected, medicine(owner(51L, "different-patient"))));
        assertFalse(StockRunningOutAlert.StockAlertViewProvider.mayLaunchStockEditor(
                captured, selected, medicine(owner(52L, "synthetic-registered-patient"))));
        assertFalse(StockRunningOutAlert.StockAlertViewProvider.mayLaunchStockEditor(
                captured, selected, null));
        assertFalse(StockRunningOutAlert.StockAlertViewProvider.mayLaunchStockEditor(
                owner(51L, null), selected, medicine(selected)));
    }
}
