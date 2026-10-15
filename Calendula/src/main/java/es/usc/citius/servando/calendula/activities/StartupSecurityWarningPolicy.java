/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import android.content.SharedPreferences;

/** Explicit user consent is required before bypassing a startup security warning. */
final class StartupSecurityWarningPolicy {
    private StartupSecurityWarningPolicy() {
    }

    static boolean wasAccepted(SharedPreferences settings, String preferenceKey) {
        try {
            return settings.getBoolean(preferenceKey, false);
        } catch (ClassCastException corruptedValue) {
            // Restored old types are not acceptance. Do not log preference
            // contents or silently bypass the environment security warning.
            return false;
        }
    }
}
