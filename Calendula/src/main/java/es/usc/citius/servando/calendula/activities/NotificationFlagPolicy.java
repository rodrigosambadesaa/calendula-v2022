/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

/** Safe reading of restored Boolean switches used for medication notices. */
final class NotificationFlagPolicy {
    private NotificationFlagPolicy() {
    }

    static boolean configuredOrDefault(PreferenceKeys key, boolean fallback) {
        try {
            return PreferenceUtils.getBoolean(key, fallback);
        } catch (ClassCastException incompatible) {
            // A corrupt restored switch must not suppress medication notices,
            // trigger an unrequested lockscreen alarm, or log private data.
            return fallback;
        }
    }
}
