package es.usc.citius.servando.calendula.activities;

import android.content.Intent;
import android.os.BadParcelableException;
import android.os.Build;

import es.usc.citius.servando.calendula.drugdb.model.persistence.Prescription;

/**
 * Never trust restored prescription search results until their extras have
 * been decoded safely. An invalid parcel is not permission to select a
 * different medicine or patient in the editor.
 */
final class MedicineSearchResultPolicy {
    private MedicineSearchResultPolicy() {
    }

    static String nameOrNull(Intent result) {
        if (result == null) {
            return null;
        }
        try {
            String name = result.getStringExtra(
                    MedicinesSearchActivity.RETURN_EXTRA_PRESCRIPTION_NAME);
            return name == null || name.trim().isEmpty() || name.length() > 4096
                    ? null : name;
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    static Prescription prescriptionOrNull(Intent result) {
        if (result == null) {
            return null;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return result.getParcelableExtra(
                        MedicinesSearchActivity.RETURN_EXTRA_PRESCRIPTION,
                        Prescription.class);
            }
            return result.getParcelableExtra(
                    MedicinesSearchActivity.RETURN_EXTRA_PRESCRIPTION);
        } catch (BadParcelableException | ClassCastException | IllegalArgumentException malformed) {
            return null;
        }
    }
}
