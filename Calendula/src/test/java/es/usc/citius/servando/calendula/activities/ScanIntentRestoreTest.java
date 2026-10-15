package es.usc.citius.servando.calendula.activities;

import android.content.Intent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ScanIntentRestoreTest {
    @Test
    public void wrongTypeRouteDataCannotStartPatientLinkScanner() {
        Intent wrong = new Intent()
                .putExtra("after_scan_pkg", 99)
                .putExtra("patient_id", "incorrect type");
        assertNull(ScanActivity.safeStringExtra(wrong, "after_scan_pkg"));
        assertEquals(-1L, ScanActivity.safeLongExtra(wrong, "patient_id"));
    }

    @Test
    public void legitimateInternalScanRouteAndOwnerArePreserved() {
        Intent valid = new Intent()
                .putExtra("after_scan_pkg", "es.usc.citius.servando.calendula")
                .putExtra("patient_id", 123L);
        assertEquals("es.usc.citius.servando.calendula",
                ScanActivity.safeStringExtra(valid, "after_scan_pkg"));
        assertEquals(123L, ScanActivity.safeLongExtra(valid, "patient_id"));
    }

    @Test
    public void missingScanResultCannotBeTreatedAsValidOwner() {
        assertEquals(-1L, ScanActivity.safeLongExtra(null, "patient_id"));
        assertNull(ScanActivity.safeStringExtra(null, "after_scan_pkg"));
    }
}
