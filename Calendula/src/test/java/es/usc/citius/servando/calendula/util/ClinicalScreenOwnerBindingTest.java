/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util;

import android.os.Bundle;
import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.persistence.Patient;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** No real patient records or credentials: all codes are disposable fixtures. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ClinicalScreenOwnerBindingTest {
    private static Patient patient(long id, String code) {
        Patient patient = new Patient();
        patient.setId(id);
        patient.setCode(code);
        return patient;
    }

    @Test
    public void persistedOwnerAndSameCodeReloadAuthorizeEitherClinicalScreen() {
        Patient selected = patient(930000100L, "synthetic-stable-owner");
        ClinicalScreenOwnerBinding binding =
                ClinicalScreenOwnerBinding.restoreOrCapture(null, selected, "medicine-editor");
        assertNotNull(binding);
        assertEquals(selected.getId(), binding.getId());
        assertTrue(binding.matches(patient(930000100L, "synthetic-stable-owner")));
        assertFalse(binding.matches(patient(930000100L, "synthetic-replaced-owner")));
        assertFalse(binding.matches(patient(930000101L, "synthetic-stable-owner")));
    }

    @Test
    public void parcelRestorationRejectsRecycledPatientAndDoesNotExposeRawCode() {
        Patient original = patient(930000200L, "synthetic-private-local-identity");
        ClinicalScreenOwnerBinding binding =
                ClinicalScreenOwnerBinding.restoreOrCapture(null, original, "medicine-editor");
        Bundle saved = new Bundle();
        binding.save(saved);
        String fingerprint = saved.getString("calendula.clinical_screen.owner_fingerprint");
        assertNotNull(fingerprint);
        assertTrue(fingerprint.matches("[0-9a-f]{64}"));
        assertFalse(saved.toString().contains(original.getCode()));

        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeBundle(saved);
            parcel.setDataPosition(0);
            Bundle roundTrip = parcel.readBundle(getClass().getClassLoader());
            assertNotNull(ClinicalScreenOwnerBinding.restoreOrCapture(
                    roundTrip, patient(930000200L, original.getCode()), "medicine-editor"));
            assertNull(ClinicalScreenOwnerBinding.restoreOrCapture(
                    roundTrip, patient(930000200L, "synthetic-restored-successor"),
                    "medicine-editor"));
            assertNull(ClinicalScreenOwnerBinding.restoreOrCapture(
                    roundTrip, original, "medicine-info"));
        } finally {
            parcel.recycle();
        }
    }

    @Test
    public void unidentifiedOrLegacySavedStateFailsClosed() {
        Patient valid = patient(930000300L, "synthetic-owner");
        assertNull(ClinicalScreenOwnerBinding.restoreOrCapture(
                null, patient(930000300L, null), "medicine-editor"));
        assertNull(ClinicalScreenOwnerBinding.restoreOrCapture(
                null, patient(930000300L, "  "), "medicine-editor"));
        assertNull(ClinicalScreenOwnerBinding.restoreOrCapture(
                null, patient(0L, "synthetic-owner"), "medicine-editor"));
        assertNull(ClinicalScreenOwnerBinding.restoreOrCapture(
                new Bundle(), valid, "medicine-editor"));

        Bundle legacyIdOnly = new Bundle();
        legacyIdOnly.putLong("STATE_OPENED_PATIENT_ID", valid.getId());
        assertNull(ClinicalScreenOwnerBinding.restoreOrCapture(
                legacyIdOnly, valid, "medicine-editor"));

        Bundle wrongType = new Bundle();
        wrongType.putString("calendula.clinical_screen.owner_id", "930000300");
        wrongType.putString("calendula.clinical_screen.owner_fingerprint",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        assertNull(ClinicalScreenOwnerBinding.restoreOrCapture(
                wrongType, valid, "medicine-editor"));
    }

    @Test
    public void savedBindingRejectsChangedOrMissingDomainAndCode() {
        Patient original = patient(930000400L, "synthetic-owner");
        ClinicalScreenOwnerBinding binding =
                ClinicalScreenOwnerBinding.restoreOrCapture(null, original, "medicine-info");
        Bundle saved = new Bundle();
        binding.save(saved);
        assertNull(ClinicalScreenOwnerBinding.restoreOrCapture(
                saved, patient(930000400L, null), "medicine-info"));
        assertNull(ClinicalScreenOwnerBinding.restoreOrCapture(
                saved, original, "medicine-editor"));
        assertNull(ClinicalScreenOwnerBinding.restoreOrCapture(
                saved, original, " "));
    }
}
