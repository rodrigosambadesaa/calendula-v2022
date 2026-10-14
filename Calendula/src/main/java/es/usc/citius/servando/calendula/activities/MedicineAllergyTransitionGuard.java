/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import java.util.Objects;

/** Pure policy for invalidating alerts tied to an older drug prescription. */
final class MedicineAllergyTransitionGuard {
    private MedicineAllergyTransitionGuard() {
    }

    static boolean prescriptionChanged(String previousCode, String previousDatabase,
                                       String nextCode, String nextDatabase) {
        return !Objects.equals(previousCode, nextCode)
                || !Objects.equals(previousDatabase, nextDatabase);
    }
}
