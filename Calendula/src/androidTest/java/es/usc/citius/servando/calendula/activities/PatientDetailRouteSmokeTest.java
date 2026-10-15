/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import android.content.Intent;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Android API matrix: verifies real Intent extras; does not launch a profile. */
@RunWith(AndroidJUnit4.class)
public class PatientDetailRouteSmokeTest {
    @Test
    public void corruptExtrasDoNotBecomeNewOrCrossPatientProfileRoutes() {
        assertEquals(Long.valueOf(-1L),
                PatientDetailActivity.requestedPatientIdOrNull(new Intent()));
        assertEquals(Long.valueOf(9001L),
                PatientDetailActivity.requestedPatientIdOrNull(
                        new Intent().putExtra("patient_id", 9001L)));
        assertNull(PatientDetailActivity.requestedPatientIdOrNull(
                new Intent().putExtra("patient_id", 9001)));
        assertNull(PatientDetailActivity.requestedPatientIdOrNull(
                new Intent().putExtra("patient_id", "9001")));
        assertNull(PatientDetailActivity.requestedPatientIdOrNull(
                new Intent().putExtra("patient_id", false)));
    }
}
