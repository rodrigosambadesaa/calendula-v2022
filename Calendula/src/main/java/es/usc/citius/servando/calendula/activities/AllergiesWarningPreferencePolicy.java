/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import android.content.SharedPreferences;

import es.usc.citius.servando.calendula.util.PreferenceKeys;

/** Consent-like safety warnings cannot use view-only preference fallbacks. */
final class AllergiesWarningPreferencePolicy {
    private AllergiesWarningPreferencePolicy() {
    }

    /**
     * Only a persisted Boolean true acknowledges the experimental-allergies
     * warning. Missing or type-corrupted preferences must display the notice.
     * This policy never overwrites imported preferences or clinical data.
     */
    static boolean shouldShowWarning(SharedPreferences preferences) {
        if (preferences == null) {
            return true;
        }
        try {
            return !preferences.getBoolean(PreferenceKeys.ALLERGIES_WARNING_SHOWN.key(), false);
        } catch (ClassCastException malformedAcknowledgement) {
            return true;
        }
    }
}
