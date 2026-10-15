package es.usc.citius.servando.calendula.activities;

import android.content.Intent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.drugdb.model.persistence.Prescription;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(AndroidJUnit4.class)
public class MedicineSearchResultSafetySmokeTest {
    @Test
    public void malformedResultDoesNotChangeOrCrashMedicineEditor() {
        Intent malformed = new Intent();
        malformed.putExtra(MedicinesSearchActivity.RETURN_EXTRA_PRESCRIPTION_NAME, 43);
        malformed.putExtra(MedicinesSearchActivity.RETURN_EXTRA_PRESCRIPTION, "invalid");
        assertNull(MedicineSearchResultPolicy.nameOrNull(malformed));
        assertNull(MedicineSearchResultPolicy.prescriptionOrNull(malformed));
    }

    @Test
    public void validResultStillWorksAcrossAndroidParcelVersions() {
        Prescription selected = new Prescription();
        Intent valid = new Intent().putExtra(
                MedicinesSearchActivity.RETURN_EXTRA_PRESCRIPTION, selected);
        assertNotNull(MedicineSearchResultPolicy.prescriptionOrNull(valid));
        Intent simple = new Intent().putExtra(
                MedicinesSearchActivity.RETURN_EXTRA_PRESCRIPTION_NAME, "safe test name");
        assertEquals("safe test name", MedicineSearchResultPolicy.nameOrNull(simple));
    }
}
