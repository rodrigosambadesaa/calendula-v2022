/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Pure route parsing; no patient database or persisted preferences. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PatientDetailRoutePolicyTest {
    @Test
    public void absentPatientIdPreservesNewProfileRoute() {
        assertEquals(Long.valueOf(-1L), PatientDetailActivity.requestedPatientIdOrNull(null));
        assertEquals(Long.valueOf(-1L),
                PatientDetailActivity.requestedPatientIdOrNull(new Intent()));
        assertEquals(Long.valueOf(-1L),
                PatientDetailActivity.requestedPatientIdOrNull(
                        new Intent().putExtra("patient_id", -1L)));
    }

    @Test
    public void validLongIdPreservesExistingProfileRoute() {
        assertEquals(Long.valueOf(424242L),
                PatientDetailActivity.requestedPatientIdOrNull(
                        new Intent().putExtra("patient_id", 424242L)));
    }

    @Test
    public void invalidExplicitIdsNeverFallBackToCreatingNewPatient() {
        assertNull(PatientDetailActivity.requestedPatientIdOrNull(
                new Intent().putExtra("patient_id", "wrong type")));
        assertNull(PatientDetailActivity.requestedPatientIdOrNull(
                new Intent().putExtra("patient_id", true)));
        assertNull(PatientDetailActivity.requestedPatientIdOrNull(
                new Intent().putExtra("patient_id", 424242)));
        assertNull(PatientDetailActivity.requestedPatientIdOrNull(
                new Intent().putExtra("patient_id", (String) null)));
    }
}
