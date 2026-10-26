/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.Arrays;
import java.util.Collections;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Request cancellation and patient isolation without an Android emulator. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ActiveMedicationOwnerGuardTest {
    @Test
    public void onlyCurrentRequestForCurrentPatientCanPopulateTheList() {
        assertTrue(ActiveMedicationOwnerGuard.canApply(5L, 5L, 200L, 200L));
        assertFalse(ActiveMedicationOwnerGuard.canApply(4L, 5L, 200L, 200L));
        assertFalse(ActiveMedicationOwnerGuard.canApply(5L, 5L, 200L, 201L));
        assertFalse(ActiveMedicationOwnerGuard.canApply(5L, 5L, null, 200L));
        assertFalse(ActiveMedicationOwnerGuard.canApply(5L, 5L, -1L, -1L));
    }

    @Test
    public void multipleOrForeignOfficialMedicineLinksNeverSelectAnArbitraryLocalRecord() {
        Patient a = new Patient();
        a.setId(101L);
        Patient b = new Patient();
        b.setId(102L);
        Medicine first = new Medicine("Synthetic first");
        first.setId(201L);
        first.setPatient(a);
        Medicine second = new Medicine("Synthetic second");
        second.setId(202L);
        second.setPatient(a);
        Medicine foreign = new Medicine("Synthetic foreign");
        foreign.setId(203L);
        foreign.setPatient(b);
        assertSame(first, ActiveMedicationOwnerGuard.uniqueOwnedLocalMedicine(
                Collections.singletonList(first), 101L));
        assertNull(ActiveMedicationOwnerGuard.uniqueOwnedLocalMedicine(
                Arrays.asList(first, second), 101L));
        assertNull(ActiveMedicationOwnerGuard.uniqueOwnedLocalMedicine(
                Arrays.asList(first, foreign), 101L));
        assertNull(ActiveMedicationOwnerGuard.uniqueOwnedLocalMedicine(
                Collections.singletonList(foreign), 101L));
        assertNull(ActiveMedicationOwnerGuard.uniqueOwnedLocalMedicine(
                Collections.singletonList(first), 102L));
        assertNull(ActiveMedicationOwnerGuard.uniqueOwnedLocalMedicine(
                Collections.emptyList(), 101L));
        assertNull(ActiveMedicationOwnerGuard.uniqueOwnedLocalMedicine(null, 101L));
    }

    @Test
    public void noCardCanBeOpenedForANonActiveOrDeletedPatient() {
        assertTrue(ActiveMedicationOwnerGuard.matchingPatient(200L, 200L, 200L));
        assertFalse(ActiveMedicationOwnerGuard.matchingPatient(200L, 201L, 200L));
        assertFalse(ActiveMedicationOwnerGuard.matchingPatient(200L, 200L, 201L));
        assertFalse(ActiveMedicationOwnerGuard.matchingPatient(null, 200L, 200L));
        assertFalse(ActiveMedicationOwnerGuard.matchingPatient(200L, null, 200L));
        assertFalse(ActiveMedicationOwnerGuard.matchingPatient(0L, 0L, 0L));
    }
}
