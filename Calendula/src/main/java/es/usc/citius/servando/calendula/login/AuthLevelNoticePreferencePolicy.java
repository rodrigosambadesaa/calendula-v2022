/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.login;

import android.content.SharedPreferences;

import es.usc.citius.servando.calendula.util.PreferenceKeys;

/**
 * The previous authorization-level rejection warning is an informational
 * UI flag, not authentication/authorization state. Only a well-typed true
 * shows it; malformed legacy preferences cannot crash the login screen.
 */
final class AuthLevelNoticePreferencePolicy {
    private AuthLevelNoticePreferencePolicy() { }

    static boolean isPending(SharedPreferences preferences) {
        if (preferences == null) {
            return false;
        }
        try {
            return preferences.getBoolean(
                    PreferenceKeys.LAST_LOGIN_FAILED_AUTH_LEVEL.key(), false);
        } catch (ClassCastException malformedNotice) {
            // Never rewrite credentials, authorize a user or log saved values.
            return false;
        }
    }
}
