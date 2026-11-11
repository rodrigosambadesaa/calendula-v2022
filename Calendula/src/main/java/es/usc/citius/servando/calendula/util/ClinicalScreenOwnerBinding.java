/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util;

import android.os.Bundle;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import es.usc.citius.servando.calendula.persistence.Patient;

/**
 * Preserves the stable owner of a clinical screen across Android recreation.
 * An SQLite row ID alone is not a stable identity after a database restore.
 *
 * A screen-specific, domain-separated digest is stored in the Bundle instead
 * of a raw provider subject/patient code. This is an integrity binding for
 * existing local identity, not a replacement for persisted-owner authorization.
 */
public final class ClinicalScreenOwnerBinding {
    private static final String KEY_ID = "calendula.clinical_screen.owner_id";
    private static final String KEY_FINGERPRINT = "calendula.clinical_screen.owner_fingerprint";

    private final Long id;
    private final String digest;
    private final String domain;

    private ClinicalScreenOwnerBinding(Long id, String digest, String domain) {
        this.id = id;
        this.digest = digest;
        this.domain = domain;
    }

    public Long getId() {
        return id;
    }

    public static ClinicalScreenOwnerBinding restoreOrCapture(
            Bundle savedState, Patient active, String domain) {
        String fingerprint = fingerprint(active, domain);
        if (fingerprint == null) {
            return null;
        }
        if (savedState == null) {
            return new ClinicalScreenOwnerBinding(active.getId(), fingerprint, domain);
        }
        try {
            Object restoredId = savedState.get(KEY_ID);
            Object restoredFingerprint = savedState.get(KEY_FINGERPRINT);
            if (restoredId instanceof Long && ((Long) restoredId) > 0L
                    && restoredId.equals(active.getId())
                    && fingerprint.equals(restoredFingerprint)) {
                return new ClinicalScreenOwnerBinding((Long) restoredId,
                        fingerprint, domain);
            }
        } catch (RuntimeException ignored) {
            // Missing, malformed, or pre-upgrade ID-only state fails closed.
        }
        return null;
    }

    public boolean matches(Patient patient) {
        return id != null && id > 0L && id.equals(patient == null ? null : patient.getId())
                && digest != null && digest.equals(fingerprint(patient, domain));
    }

    public void save(Bundle outState) {
        if (outState != null && id != null && id > 0L
                && digest != null && digest.matches("[0-9a-f]{64}")) {
            outState.putLong(KEY_ID, id);
            outState.putString(KEY_FINGERPRINT, digest);
        }
    }

    private static String fingerprint(Patient patient, String domain) {
        if (!PatientOwnerGuard.hasStableIdentity(patient)
                || domain == null || domain.trim().isEmpty()) {
            return null;
        }
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(
                    ("calendula:clinical-screen:v1:" + domain + ":" + patient.getId()
                            + ":" + patient.getCode()).getBytes(StandardCharsets.UTF_8));
            char[] hex = new char[bytes.length * 2];
            for (int i = 0; i < bytes.length; i++) {
                hex[2 * i] = Character.forDigit((bytes[i] & 0xff) >>> 4, 16);
                hex[2 * i + 1] = Character.forDigit(bytes[i] & 0x0f, 16);
            }
            return new String(hex);
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("Cannot bind clinical screen owner", unavailable);
        }
    }
}
