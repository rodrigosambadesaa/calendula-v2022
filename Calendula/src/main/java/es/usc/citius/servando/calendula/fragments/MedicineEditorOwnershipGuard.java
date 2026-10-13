/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

/** Pure owner identity policy, testable without an Android UI or database. */
final class MedicineEditorOwnershipGuard {
    private MedicineEditorOwnershipGuard() {
    }

    static boolean sameEditorPatient(Long openedPatientId, Long activePatientId) {
        return openedPatientId != null && openedPatientId > 0L
                && openedPatientId.equals(activePatientId);
    }

    static boolean canEdit(Long openedPatientId, Long activePatientId,
                           Long selectedMedicineId, Long selectedOwnerId,
                           Long currentMedicineId, Long currentOwnerId) {
        return sameEditorPatient(openedPatientId, activePatientId)
                && selectedMedicineId != null && selectedMedicineId > 0L
                && selectedMedicineId.equals(currentMedicineId)
                && openedPatientId.equals(selectedOwnerId)
                && openedPatientId.equals(currentOwnerId);
    }
}
