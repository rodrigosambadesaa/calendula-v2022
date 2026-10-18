/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Original ownership must precede both local and official medicine detail lookups. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class MedicineInfoRestoreOwnerTest {
    private static final String KEY = MedicineInfoActivity.STATE_OPENED_PATIENT_ID;

    @Test
    public void newDetailPageCanUseActivePatient() {
        assertEquals(Long.valueOf(200L),
                RestoredClinicalOwnerGuard.restoredOwnerOrNull(null, KEY, 200L));
    }

    @Test
    public void missingOriginalPatientCannotRetargetOfficialDrugCode() {
        assertNull(RestoredClinicalOwnerGuard.restoredOwnerOrNull(
                new Bundle(), KEY, 201L));
        Bundle saved = new Bundle();
        saved.putLong(KEY, 200L);
        assertNull(RestoredClinicalOwnerGuard.restoredOwnerOrNull(saved, KEY, 201L));
        assertEquals(Long.valueOf(200L),
                RestoredClinicalOwnerGuard.restoredOwnerOrNull(saved, KEY, 200L));
    }

    @Test
    public void malformedAndInvalidOriginalOwnerAreRejected() {
        Bundle state = new Bundle();
        state.putString(KEY, "200");
        assertNull(RestoredClinicalOwnerGuard.restoredOwnerOrNull(state, KEY, 200L));
        state.putInt(KEY, 200);
        assertNull(RestoredClinicalOwnerGuard.restoredOwnerOrNull(state, KEY, 200L));
        state.putLong(KEY, -1L);
        assertNull(RestoredClinicalOwnerGuard.restoredOwnerOrNull(state, KEY, 200L));
    }

    @Test
    @Config(sdk = 33)
    public void android13RespectsSameOriginalPatientRule() {
        Bundle state = new Bundle();
        state.putLong(KEY, 200L);
        assertNull(RestoredClinicalOwnerGuard.restoredOwnerOrNull(state, KEY, 201L));
        assertEquals(Long.valueOf(200L),
                RestoredClinicalOwnerGuard.restoredOwnerOrNull(state, KEY, 200L));
    }
}
