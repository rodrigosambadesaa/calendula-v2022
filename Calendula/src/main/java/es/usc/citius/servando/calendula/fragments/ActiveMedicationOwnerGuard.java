/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

/** Snapshot isolation policy for asynchronous provider-medication results. */
final class ActiveMedicationOwnerGuard {
    private ActiveMedicationOwnerGuard() {
    }

    static boolean matchingPatient(Long requestedPatientId, Long currentPatientId,
                                   Long rowOwnerId) {
        return requestedPatientId != null && requestedPatientId > 0L
                && requestedPatientId.equals(currentPatientId)
                && requestedPatientId.equals(rowOwnerId);
    }

    static boolean canApply(long requestVersion, long latestVersion,
                            Long requestedPatientId, Long activePatientId) {
        return requestVersion == latestVersion
                && matchingPatient(requestedPatientId, activePatientId, requestedPatientId);
    }
}
