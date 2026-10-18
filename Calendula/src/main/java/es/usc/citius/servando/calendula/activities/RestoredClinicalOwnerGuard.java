/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import android.os.Bundle;

/**
 * Clinical screens must preserve the original patient through process death.
 * A non-null but incomplete restored state must never adopt the newly active
 * patient and display old form data under a different patient identity.
 */
final class RestoredClinicalOwnerGuard {
    private RestoredClinicalOwnerGuard() { }

    /**
     * Return the currently active owner only for a genuinely fresh launch.
     * On restoration, return the saved owner only when it is a positive Long
     * matching that patient. Wrong-type or unreadable Bundles fail closed.
     */
    static Long restoredOwnerOrNull(Bundle savedState, String key, Long activeOwnerId) {
        if (activeOwnerId == null || activeOwnerId <= 0L) {
            return null;
        }
        if (savedState == null) {
            return activeOwnerId;
        }
        if (key == null || key.isEmpty()) {
            return null;
        }
        try {
            if (!savedState.containsKey(key)) {
                return null;
            }
            Object value = savedState.get(key);
            if (!(value instanceof Long)) {
                return null;
            }
            Long restored = (Long) value;
            return restored > 0L && restored.equals(activeOwnerId) ? restored : null;
        } catch (RuntimeException malformedState) {
            return null;
        }
    }
}
