/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

/** Identity policy shared by asynchronous allergy search and edit screens. */
final class AllergenSearchOwnerGuard {
    private AllergenSearchOwnerGuard() { }

    static boolean samePatient(Long openedPatientId, Long activePatientId) {
        return openedPatientId != null && openedPatientId > 0L
                && openedPatientId.equals(activePatientId);
    }

    static boolean canStore(Long openedPatientId, Long activePatientId,
                            Long recordOwnerId) {
        return samePatient(openedPatientId, activePatientId)
                && openedPatientId.equals(recordOwnerId);
    }
}
