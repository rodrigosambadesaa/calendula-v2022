/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util;

import es.usc.citius.servando.calendula.persistence.Patient;

/**
 * Prevent a conditional FHIR revision from following a different local
 * patient or authenticated remote subject across account/profile changes.
 * Legacy unowned revisions intentionally trigger a fresh provider fetch.
 */
public final class ProviderRevisionGuard {
    private ProviderRevisionGuard() {
    }

    /** Stable per-record identity; do not log or export this clinical scope. */
    public static String ownerKey(Long patientId, String authenticatedSubject) {
        if (patientId == null || patientId <= 0L
                || authenticatedSubject == null || authenticatedSubject.trim().isEmpty()) {
            return null;
        }
        return patientId + ":" + authenticatedSubject;
    }

    public static boolean isSameOwner(String expectedOwner, String recordedOwner) {
        return expectedOwner != null && expectedOwner.equals(recordedOwner);
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
