/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.persistence.alerts;

import java.util.List;

/** Pure, Android-independent rule: never present partial allergy details as complete. */
final class AllergyAlertPresentationGuard {
    private AllergyAlertPresentationGuard() {
    }

    static boolean hasCompleteNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return false;
        }
        for (String name : names) {
            if (name == null || name.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
