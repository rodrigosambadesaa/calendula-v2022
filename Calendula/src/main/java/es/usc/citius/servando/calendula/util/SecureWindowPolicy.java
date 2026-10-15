/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util;

/**
 * Screens containing clinical information must be protected unless a user
 * explicitly opts out. A missing or incorrectly typed restored preference
 * is not consent to expose screenshots or Recents thumbnails.
 */
public final class SecureWindowPolicy {
    private SecureWindowPolicy() {
    }

    public static boolean isProtected() {
        try {
            // Keep this aligned with the true default in pref_privacy.xml.
            return PreferenceUtils.getBoolean(PreferenceKeys.SECURE_WINDOW, true);
        } catch (ClassCastException malformed) {
            // Do not log potentially private preference values.
            return true;
        }
    }
}
