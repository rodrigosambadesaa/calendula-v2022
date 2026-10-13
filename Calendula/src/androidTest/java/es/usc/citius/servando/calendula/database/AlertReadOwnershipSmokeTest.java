/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.database;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.joda.time.LocalDate;
import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.persistence.alerts.StockRunningOutAlert;

import static org.junit.Assert.*;

/** Synthetic cross-patient alert test, with no destructive database resets. */
@RunWith(AndroidJUnit4.class)
public class AlertReadOwnershipSmokeTest {
    @Test
    public void medicineReadQueriesRejectRowsBelongingToAnotherPatient() throws Exception {
        assertTrue(DB.initialized);
        String seed = Long.toString(System.nanoTime());
        Patient owner = new Patient();
        owner.setCode("ci-alert-read-owner-" + seed);
        owner.setName("Synthetic medicine owner");
        Patient foreign = new Patient();
        foreign.setCode("ci-alert-read-foreign-" + seed);
        foreign.setName("Synthetic alert foreign owner");
        Medicine medicine = new Medicine("Synthetic alert read medicine", Presentation.PILLS);
        StockRunningOutAlert warning = null;
        try {
            assertEquals(1, DB.patients().create(owner));
            assertEquals(1, DB.patients().create(foreign));
            medicine.setPatient(owner);
            assertEquals(1, DB.medicines().create(medicine));
            warning = new StockRunningOutAlert(medicine, LocalDate.now());
            assertEquals(1, DB.alerts().create(warning));
            final int warningLevel = warning.getLevel();
            final String warningType = warning.getType();
            assertEquals(1, DB.alerts().findByMedicineSortByLevel(medicine).size());
            assertEquals(1, DB.alerts().findByMedicineAndLevel(
                    medicine, warningLevel).size());
            assertEquals(1, DB.alerts().findByMedicineAndType(
                    medicine, warningType).size());
            assertEquals(1, DB.alerts().findByPatientMedicineAndType(
                    owner, medicine, warningType).size());

            // A damaged import can associate the same medicine with another
            // patient's alert. No reader must return that cross-owner row.
            warning.setPatient(foreign);
            assertEquals(1, DB.alerts().update(warning));
            assertRejected(() -> DB.alerts().findByMedicineSortByLevel(medicine));
            assertRejected(() -> DB.alerts().findByMedicineAndLevel(
                    medicine, warningLevel));
            assertRejected(() -> DB.alerts().findByMedicineAndType(
                    medicine, warningType));
            assertRejected(() -> DB.alerts().findByPatientMedicineAndType(
                    owner, medicine, warningType));

            Medicine forged = new Medicine("Stale owner", Presentation.PILLS);
            forged.setId(medicine.getId());
            forged.setPatient(foreign);
            assertRejected(() -> DB.alerts().findByMedicineSortByLevel(forged));
        } finally {
            if (warning != null && warning.id() != null
                    && DB.alerts().findById(warning.id()) != null)
                DB.alerts().deleteById(warning.id());
            if (medicine.getId() != null && DB.medicines().findById(medicine.getId()) != null)
                DB.medicines().deleteById(medicine.getId());
            if (foreign.getId() != null && DB.patients().findById(foreign.getId()) != null)
                DB.patients().deleteById(foreign.getId());
            if (owner.getId() != null && DB.patients().findById(owner.getId()) != null)
                DB.patients().deleteById(owner.getId());
        }
    }

    private static void assertRejected(Runnable read) {
        boolean rejected = false;
        try {
            read.run();
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        assertTrue("Cross-patient alert rows must not be returned", rejected);
    }
}
