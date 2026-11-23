/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

import android.os.Bundle;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.util.PatientOwnerGuard;

/**
 * Restored routine forms must not follow a new owner after SQLite row-ID
 * reuse. A domain-separated digest binds the UI state without persisting a
 * provider subject or patient's raw stable code in the Android Bundle.
 */
final class RoutineEditorOwnerState {
    static final String ORIGINAL_OWNER = "routine_editor_original_patient";
    static final String ORIGINAL_OWNER_FINGERPRINT = "routine_editor_original_patient_fingerprint";

    static final class OwnerBinding {
        final Long id;
        final String fingerprint;

        private OwnerBinding(Long id, String fingerprint) {
            this.id = id;
            this.fingerprint = fingerprint;
        }
    }

    private RoutineEditorOwnerState() { }

    private static String fingerprint(Patient patient) {
        if (!PatientOwnerGuard.hasStableIdentity(patient)) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(("calendula:routine-editor:owner:v1:"
                            + patient.getId() + ":" + patient.getCode())
                            .getBytes(StandardCharsets.UTF_8));
            char[] hex = new char[digest.length * 2];
            for (int i = 0; i < digest.length; i++) {
                hex[2 * i] = Character.forDigit((digest[i] & 0xff) >>> 4, 16);
                hex[2 * i + 1] = Character.forDigit(digest[i] & 0x0f, 16);
            }
            return new String(hex);
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("Cannot bind routine editor owner", unavailable);
        }
    }

    static OwnerBinding restoredOwnerOrNull(Bundle restoredState, Patient active) {
        String activeFingerprint = fingerprint(active);
        if (activeFingerprint == null) {
            return null;
        }
        if (restoredState == null) {
            return new OwnerBinding(active.getId(), activeFingerprint);
        }
        try {
            Object savedId = restoredState.get(ORIGINAL_OWNER);
            Object savedFingerprint = restoredState.get(ORIGINAL_OWNER_FINGERPRINT);
            if (savedId instanceof Long && ((Long) savedId) > 0L
                    && savedId.equals(active.getId())
                    && activeFingerprint.equals(savedFingerprint)) {
                return new OwnerBinding((Long) savedId, activeFingerprint);
            }
        } catch (RuntimeException malformedState) {
            // Malformed or legacy saved state may never silently rebind a
            // clinical routine draft to a new patient with a recycled ID.
        }
        return null;
    }

    static boolean stillOwnedBy(OwnerBinding original, Patient active) {
        return original != null && original.id != null && original.id > 0L
                && original.id.equals(active == null ? null : active.getId())
                && original.fingerprint.equals(fingerprint(active));
    }

    static void saveBinding(Bundle state, OwnerBinding binding) {
        if (state != null && binding != null && binding.id != null
                && binding.id > 0L && binding.fingerprint != null
                && binding.fingerprint.matches("[0-9a-f]{64}")) {
            state.putLong(ORIGINAL_OWNER, binding.id);
            state.putString(ORIGINAL_OWNER_FINGERPRINT, binding.fingerprint);
        }
    }
}
