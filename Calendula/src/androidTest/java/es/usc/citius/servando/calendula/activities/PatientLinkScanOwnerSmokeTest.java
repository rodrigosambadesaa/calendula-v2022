package es.usc.citius.servando.calendula.activities;

import android.content.Intent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class PatientLinkScanOwnerSmokeTest {
    @Test
    public void staleActivePatientDoesNotAcceptOldQrToken() {
        assertTrue(ScannedPatientLinkGuard.isSameActiveContext(105L, 105L));
        assertFalse(ScannedPatientLinkGuard.isSameActiveContext(105L, 106L));
        assertFalse(ScannedPatientLinkGuard.isSameActiveContext(105L, null));
    }

    @Test
    public void wrongTypeSavedScanOwnerIsInvalidOnAndroid() {
        Intent wrong = new Intent().putExtra("patient_id", "105");
        assertEquals(-1L, ScanActivity.safeLongExtra(wrong, "patient_id"));
    }
}
