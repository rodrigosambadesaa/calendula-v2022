/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util;

/**
 * View-only preference recovery. Unknown types must not crash navigation,
 * nor re-enable a destructive workflow or silently mutate persisted settings.
 * Security- or clinical-consent flags should use their own stricter policies.
 */
public final class RestoredUiBoolean {
    private RestoredUiBoolean() {
    }

    public static boolean read(PreferenceKeys key, boolean fallback) {
        try {
            return PreferenceUtils.getBoolean(key, fallback);
        } catch (ClassCastException legacyType) {
            return fallback;
        }
    }
}
