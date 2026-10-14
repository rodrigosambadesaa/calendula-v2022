/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

/** Pure privacy guard for asynchronous medication lists and destructive actions. */
final class MedicineListPatientGuard {
    private MedicineListPatientGuard() {
    }

    static boolean currentReload(long requestedGeneration, long currentGeneration,
                                 Long requestedPatientId, Long activePatientId) {
        return requestedGeneration == currentGeneration
                && requestedPatientId != null && requestedPatientId > 0L
                && requestedPatientId.equals(activePatientId);
    }

    static boolean selectedMedicineIsOwned(Long selectedMedicineId,
                                           Long selectedPatientId, Long activePatientId,
                                           Long persistedMedicineId, Long persistedPatientId) {
        return selectedMedicineId != null && selectedMedicineId > 0L
                && selectedPatientId != null && selectedPatientId > 0L
                && selectedPatientId.equals(activePatientId)
                && selectedMedicineId.equals(persistedMedicineId)
                && selectedPatientId.equals(persistedPatientId);
    }
}
