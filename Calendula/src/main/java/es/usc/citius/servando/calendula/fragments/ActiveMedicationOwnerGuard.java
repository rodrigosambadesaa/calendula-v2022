/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

import java.util.List;
import es.usc.citius.servando.calendula.persistence.Medicine;

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

    /** Never open an arbitrary medicine when several provider links exist. */
    static Medicine uniqueOwnedLocalMedicine(List<Medicine> links, Long patientId) {
        if (patientId == null || patientId <= 0L || links == null || links.size() != 1) {
            return null;
        }
        Medicine candidate = links.get(0);
        if (candidate == null || candidate.getId() == null || candidate.getId() <= 0L
                || candidate.getPatient() == null || candidate.getPatient().getId() == null
                || !patientId.equals(candidate.getPatient().getId())) {
            return null;
        }
        return candidate;
    }

    static boolean canApply(long requestVersion, long latestVersion,
                            Long requestedPatientId, Long activePatientId) {
        return requestVersion == latestVersion
                && matchingPatient(requestedPatientId, activePatientId, requestedPatientId);
    }
}
