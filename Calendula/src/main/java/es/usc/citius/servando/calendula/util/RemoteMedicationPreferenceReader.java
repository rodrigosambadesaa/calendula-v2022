/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util;

/**
 * Recover read-only provider synchronization metadata after restoring
 * incompatible legacy SharedPreferences. No patient records or tokens are
 * changed, and an invalid revision is never treated as a successful sync.
 */
public final class RemoteMedicationPreferenceReader {
    private RemoteMedicationPreferenceReader() {
    }

    public static String readOrDefault(PreferenceKeys key, String fallback) {
        try {
            return PreferenceUtils.getString(key, fallback);
        } catch (ClassCastException incompatible) {
            return fallback;
        }
    }

    /**
     * A restored revision is untrusted state. Apply the same format rules as
     * fresh provider revisions before using it in a conditional query.
     * A malformed value must trigger a complete fetch, not a stale 304.
     */
    static boolean isUsableCachedRevision(String revision) {
        if (revision == null || revision.trim().isEmpty()
                || "null".equalsIgnoreCase(revision.trim())) {
            return false;
        }
        for (int i = 0; i < revision.length(); i++) {
            if (Character.isISOControl(revision.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Legacy global summaries have no trustworthy owner: do not show them.
     * New notification summaries are explicitly tied to the persisted
     * patient and authenticated account subject.
     */
    public static String summaryForPatientOrEmpty(
            es.usc.citius.servando.calendula.persistence.Patient patient) {
        if (patient == null) {
            return "";
        }
        String expectedOwner = ProviderRevisionGuard.ownerKey(
                patient.getId(), patient.getCode());
        String recordedOwner = readOrDefault(
                PreferenceKeys.REMOTE_LAST_GOOD_UPDATE_SUMMARY_OWNER, null);
        return ProviderRevisionGuard.isSameOwner(expectedOwner, recordedOwner)
                ? readOrDefault(PreferenceKeys.REMOTE_LAST_GOOD_UPDATE_SUMMARY, "")
                : "";
    }

    /** Never normalize valid opaque server revisions. */
    public static String lastRevisionOrNull() {
        String revision = readOrDefault(PreferenceKeys.REMOTE_LAST_HASH, null);
        return isUsableCachedRevision(revision) ? revision : null;
    }
}
