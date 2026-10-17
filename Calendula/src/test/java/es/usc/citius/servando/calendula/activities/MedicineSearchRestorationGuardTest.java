package es.usc.citius.servando.calendula.activities;

import android.content.Intent;
import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Restored medicine search context and scanner extras are always untrusted. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class MedicineSearchRestorationGuardTest {
    @Test
    public void freshLaunchUsesActivePatientButRestorationRequiresOriginalOwner() {
        assertEquals(Long.valueOf(170L),
                MedicineSearchOwnerGuard.restoredOwnerOrNull(null, 170L));

        Bundle state = new Bundle();
        state.putLong(MedicineSearchOwnerGuard.SAVED_OWNER_KEY, 170L);
        Long restored = MedicineSearchOwnerGuard.restoredOwnerOrNull(state, 170L);
        assertTrue(MedicineSearchOwnerGuard.samePatient(restored, 170L));
        assertFalse(MedicineSearchOwnerGuard.samePatient(restored, 171L));
    }

    @Test
    public void missingOrMalformedRestoredOwnerNeverBecomesActivePatient() {
        assertNull(MedicineSearchOwnerGuard.restoredOwnerOrNull(new Bundle(), 170L));
        Bundle wrongType = new Bundle();
        wrongType.putString(MedicineSearchOwnerGuard.SAVED_OWNER_KEY, "170");
        assertNull(MedicineSearchOwnerGuard.restoredOwnerOrNull(wrongType, 170L));
        Bundle invalid = new Bundle();
        invalid.putLong(MedicineSearchOwnerGuard.SAVED_OWNER_KEY, -1L);
        assertNull(MedicineSearchOwnerGuard.restoredOwnerOrNull(invalid, 170L));
    }

    @Test
    public void malformedAndOversizedIncomingSearchOrBarcodeExtrasAreIgnored() {
        assertNull(MedicineSearchOwnerGuard.safeStringExtra(null, "search"));
        assertNull(MedicineSearchOwnerGuard.safeStringExtra(new Intent(), "search"));
        assertNull(MedicineSearchOwnerGuard.safeStringExtra(
                new Intent().putExtra("search", 123), "search"));
        assertNull(MedicineSearchOwnerGuard.safeStringExtra(
                new Intent().putExtra("search", repeat('x', 4097)), "search"));
        assertEquals("Paracetamol", MedicineSearchOwnerGuard.safeStringExtra(
                new Intent().putExtra("search", "Paracetamol"), "search"));
    }

    @Test
    @Config(sdk = 33)
    public void typedExtrasRemainSafeOnAndroid13() {
        assertNull(MedicineSearchOwnerGuard.safeStringExtra(
                new Intent().putExtra("barcode", 1234L), "barcode"));
        assertEquals("ABC123", MedicineSearchOwnerGuard.safeStringExtra(
                new Intent().putExtra("barcode", "ABC123"), "barcode"));
    }

    private static String repeat(char c, int count) {
        char[] array = new char[count];
        java.util.Arrays.fill(array, c);
        return new String(array);
    }
}
