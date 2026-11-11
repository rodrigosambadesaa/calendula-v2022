/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities.schedules;

import android.os.Bundle;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.util.PatientOwnerGuard;

/**
 * A restored schedule editor may not silently adopt a patient whose SQLite ID
 * was recycled. Persist only a domain-separated digest of the stable owner,
 * never the raw patient code, into the saved Activity state.
 */
final class ScheduleEditorOwnerState {
    private static final String OWNER_ID = "schedule_editor_original_patient_id";
    private static final String OWNER_FINGERPRINT = "schedule_editor_original_patient_fingerprint";

    static final class OwnerBinding {
        final Long id;
        final String fingerprint;

        private OwnerBinding(Long id, String fingerprint) {
            this.id = id;
            this.fingerprint = fingerprint;
        }
    }

    private ScheduleEditorOwnerState() { }

    private static String fingerprint(Patient patient) {
        if (!PatientOwnerGuard.hasStableIdentity(patient)) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    ("calendula:schedule-editor:owner:v1:" + patient.getId() + ":"
                            + patient.getCode()).getBytes(StandardCharsets.UTF_8));
            char[] hex = new char[digest.length * 2];
            for (int i = 0; i < digest.length; i++) {
                hex[2 * i] = Character.forDigit((digest[i] & 0xff) >>> 4, 16);
                hex[2 * i + 1] = Character.forDigit(digest[i] & 0x0f, 16);
            }
            return new String(hex);
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("Cannot bind schedule editor owner", unavailable);
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
            Object id = restoredState.get(OWNER_ID);
            Object digest = restoredState.get(OWNER_FINGERPRINT);
            if (id instanceof Long && ((Long) id) > 0L
                    && id.equals(active.getId())
                    && digest instanceof String
                    && activeFingerprint.equals(digest)) {
                return new OwnerBinding((Long) id, activeFingerprint);
            }
        } catch (RuntimeException malformedState) {
            // Legacy ID-only or corrupted saved drafts cannot adopt new owners.
        }
        return null;
    }

    static boolean stillOwnedBy(OwnerBinding original, Patient candidate) {
        return original != null && original.id != null && original.id > 0L
                && original.id.equals(candidate == null ? null : candidate.getId())
                && original.fingerprint != null
                && original.fingerprint.equals(fingerprint(candidate));
    }

    static void saveBinding(Bundle state, OwnerBinding binding) {
        if (state != null && binding != null && binding.id != null && binding.id > 0L
                && binding.fingerprint != null
                && binding.fingerprint.matches("[0-9a-f]{64}")) {
            state.putLong(OWNER_ID, binding.id);
            state.putString(OWNER_FINGERPRINT, binding.fingerprint);
        }
    }
}
