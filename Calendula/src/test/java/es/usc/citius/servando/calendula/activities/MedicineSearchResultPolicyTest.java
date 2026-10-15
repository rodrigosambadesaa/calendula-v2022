package es.usc.citius.servando.calendula.activities;

import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.drugdb.model.persistence.Prescription;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class MedicineSearchResultPolicyTest {
    @Test
    public void missingMalformedAndWrongTypedNamesCannotPopulateEditor() {
        assertNull(MedicineSearchResultPolicy.nameOrNull(null));
        assertNull(MedicineSearchResultPolicy.nameOrNull(new Intent()));
        assertNull(MedicineSearchResultPolicy.nameOrNull(new Intent().putExtra(
                MedicinesSearchActivity.RETURN_EXTRA_PRESCRIPTION_NAME, 14)));
        assertNull(MedicineSearchResultPolicy.nameOrNull(new Intent().putExtra(
                MedicinesSearchActivity.RETURN_EXTRA_PRESCRIPTION_NAME, "   ")));
        assertEquals("Selected medication",
                MedicineSearchResultPolicy.nameOrNull(new Intent().putExtra(
                        MedicinesSearchActivity.RETURN_EXTRA_PRESCRIPTION_NAME,
                        "Selected medication")));
    }

    @Test
    public void malformedOrMissingParcelableCannotBeAppliedToMedicine() {
        assertNull(MedicineSearchResultPolicy.prescriptionOrNull(null));
        assertNull(MedicineSearchResultPolicy.prescriptionOrNull(new Intent()));
        assertNull(MedicineSearchResultPolicy.prescriptionOrNull(new Intent().putExtra(
                MedicinesSearchActivity.RETURN_EXTRA_PRESCRIPTION, "not a prescription")));
    }

    @Test
    public void legitimateParcelableStillPassesOnLegacyAndroid() {
        Prescription selected = new Prescription();
        Prescription decoded = MedicineSearchResultPolicy.prescriptionOrNull(
                new Intent().putExtra(
                        MedicinesSearchActivity.RETURN_EXTRA_PRESCRIPTION, selected));
        org.junit.Assert.assertNotNull(decoded);
    }

    @Test
    @Config(sdk = 33)
    public void legitimateParcelableStillPassesAndroid13TypedDecoding() {
        Prescription selected = new Prescription();
        Prescription decoded = MedicineSearchResultPolicy.prescriptionOrNull(
                new Intent().putExtra(
                        MedicinesSearchActivity.RETURN_EXTRA_PRESCRIPTION, selected));
        org.junit.Assert.assertNotNull(decoded);
    }
}
