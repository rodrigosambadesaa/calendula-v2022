/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

/** Prevent deferred medication catalog results from crossing patient profiles. */
final class MedicineSearchOwnerGuard {
    private MedicineSearchOwnerGuard() { }

    static boolean samePatient(Long openedPatientId, Long currentPatientId) {
        return openedPatientId != null && openedPatientId > 0L
                && openedPatientId.equals(currentPatientId);
    }
}
