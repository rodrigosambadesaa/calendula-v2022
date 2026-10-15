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

    /** A broken or absent revision must request full verification, not 304. */
    public static String lastRevisionOrNull() {
        String revision = readOrDefault(PreferenceKeys.REMOTE_LAST_HASH, null);
        return revision == null || revision.trim().isEmpty() ? null : revision;
    }
}
