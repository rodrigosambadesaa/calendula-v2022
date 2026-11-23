/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util;

import es.usc.citius.servando.calendula.persistence.Patient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Prevent a conditional FHIR revision from following a different local
 * patient or authenticated remote subject across account/profile changes.
 * Legacy unowned revisions intentionally trigger a fresh provider fetch.
 */
public final class ProviderRevisionGuard {
    private ProviderRevisionGuard() {
    }

    /**
     * Versioned, domain-separated fingerprint for private sync metadata.
     * Neither the raw authenticated account subject nor the patient row ID
     * should be copied into SharedPreferences as an owner token.
     *
     * This SHA-256 digest is an opaque comparison key, NOT encryption or a
     * substitute for Android's private app storage. Old plaintext owner keys
     * intentionally do not match: the next request performs a full fetch.
     */
    public static String ownerKey(Long patientId, String authenticatedSubject) {
        if (patientId == null || patientId <= 0L
                || authenticatedSubject == null || authenticatedSubject.trim().isEmpty()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("calendula/provider-revision-owner/v2"
                    .getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(Long.toString(patientId).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(authenticatedSubject.getBytes(StandardCharsets.UTF_8));
            final char[] hex = "0123456789abcdef".toCharArray();
            byte[] hash = digest.digest();
            char[] encoded = new char[hash.length * 2];
            for (int i = 0; i < hash.length; i++) {
                int b = hash[i] & 0xff;
                encoded[i * 2] = hex[b >>> 4];
                encoded[i * 2 + 1] = hex[b & 0x0f];
            }
            return "sha256-v2:" + new String(encoded);
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }

    /** Reject malformed, unowned and legacy raw-subject metadata. */
    public static boolean isValidOpaqueOwnerKey(String owner) {
        return owner != null && owner.matches("sha256-v2:[0-9a-f]{64}");
    }

    public static boolean isSameOwner(String expectedOwner, String recordedOwner) {
        return isValidOpaqueOwnerKey(expectedOwner) && expectedOwner.equals(recordedOwner);
    }

    /**
     * Reuse a prior revision only for the exact persisted patient and login.
     * Corrupt preference types, absent ownership and account changes fail closed.
     */
    public static String lastRevisionForPatient(Patient patient, String authenticatedSubject) {
        if (patient == null || authenticatedSubject == null
                || !authenticatedSubject.equals(patient.getCode())) {
            return null;
        }
        String expected = ownerKey(patient.getId(), authenticatedSubject);
        String recorded = RemoteMedicationPreferenceReader.readOrDefault(
                PreferenceKeys.REMOTE_LAST_HASH_OWNER, null);
        return isSameOwner(expected, recorded)
                ? RemoteMedicationPreferenceReader.lastRevisionOrNull() : null;
    }
}
