/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import java.util.Objects;

/** Pure owner and identity checks before removing patient allergy records. */
final class AllergenDeletionGuard {
    private AllergenDeletionGuard() {
    }

    static boolean isSamePersistedOwner(Long openedPatientId, Long activePatientId,
                                        Long requestedAllergenId, Long requestedPatientId,
                                        Long persistedAllergenId, Long persistedPatientId,
                                        Object requestedType, Object persistedType,
                                        String requestedIdentifier, String persistedIdentifier) {
        return openedPatientId != null && openedPatientId > 0L
                && openedPatientId.equals(activePatientId)
                && requestedAllergenId != null && requestedAllergenId > 0L
                && requestedAllergenId.equals(persistedAllergenId)
                && openedPatientId.equals(requestedPatientId)
                && openedPatientId.equals(persistedPatientId)
                && requestedType != null && requestedType.equals(persistedType)
                // Without a reliable external identifier, deleting the
                // allergen row could strand an unverified HIGH warning that
                // cannot be safely reconciled after COMMIT.
                && requestedIdentifier != null && !requestedIdentifier.isEmpty()
                && Objects.equals(requestedIdentifier, persistedIdentifier);
    }
}
