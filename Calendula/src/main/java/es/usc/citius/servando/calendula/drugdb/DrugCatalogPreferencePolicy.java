/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.drugdb;

import android.content.SharedPreferences;

import java.util.Map;

/**
 * Read the configured medicine catalog without treating a corrupt restored
 * preference as proof that any specific catalog was installed or selected.
 *
 * This does NOT install, clear, activate, or modify any catalog or prescription.
 */
public final class DrugCatalogPreferencePolicy {
    private DrugCatalogPreferencePolicy() {
    }

    public static String selectedIdOrFallback(
            SharedPreferences settings, String preferenceKey, String fallback) {
        try {
            String selected = settings.getString(preferenceKey, fallback);
            return selected == null ? fallback : selected;
        } catch (ClassCastException incompatible) {
            // Android restore can put a non-String under a String key.
            // Never log restored preference content or mutate clinical data.
            return fallback;
        }
    }

    static <T> T knownCatalogOrDefault(
            Map<String, T> knownCatalogs, String selected, T fallback) {
        // Unknown IDs must not resolve to null, even after an upgrade that
        // removes a historical catalog provider.
        T registered = selected == null ? null : knownCatalogs.get(selected);
        return registered != null ? registered : fallback;
    }
}
