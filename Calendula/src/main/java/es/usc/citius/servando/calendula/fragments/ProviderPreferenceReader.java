/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

/** Typed, fail-closed reads for provider update metadata in restored preferences. */
final class ProviderPreferenceReader {
    private ProviderPreferenceReader() {
    }

    static String read(PreferenceKeys key, String fallback) {
        try {
            String value = PreferenceUtils.getString(key, fallback);
            return value == null ? fallback : value;
        } catch (ClassCastException wrongType) {
            // Older backups and malformed preferences may store a number or
            // boolean in a formerly string-valued provider update key.
            // Missing metadata must not crash the medication screen or stop
            // an explicitly requested provider refresh.
            return fallback;
        }
    }
}
