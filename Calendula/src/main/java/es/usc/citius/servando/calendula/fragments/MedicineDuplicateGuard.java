/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

import java.util.List;

/** Pure duplicate-record policy, independent of Android and SQLite. */
final class MedicineDuplicateGuard {
    private MedicineDuplicateGuard() {
    }

    static boolean hasConflictingMatch(Long selectedId, List<Long> matchingIds) {
        if (matchingIds == null) {
            // A failed or unavailable query must not be permission to save.
            return true;
        }
        for (Long persistedId : matchingIds) {
            if (persistedId == null || persistedId <= 0L || selectedId == null
                    || !selectedId.equals(persistedId)) {
                return true;
            }
        }
        return false;
    }
}
