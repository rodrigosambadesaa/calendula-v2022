/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

/** Prevent delayed callbacks from writing into an unrelated patient profile. */
final class MedicineSaveOwnerGuard {
    private MedicineSaveOwnerGuard() {
    }

    /**
     * A medicine editor must authorize its launch ID *before* inflating a
     * treatment form, not just when the user eventually taps Save.
     */
    static boolean canOpenForEdit(Long openedPatientId, Long activePatientId,
                                  Long selectedMedicineId, Long persistedMedicineId,
                                  Long persistedPatientId) {
        return openedPatientId != null && openedPatientId > 0L
                && openedPatientId.equals(activePatientId)
                && selectedMedicineId != null && selectedMedicineId > 0L
                && selectedMedicineId.equals(persistedMedicineId)
                && openedPatientId.equals(persistedPatientId);
    }

    static boolean canCreate(Long openedPatientId, Long activePatientId,
                             Long candidatePatientId, Long candidateMedicineId) {
        return openedPatientId != null && openedPatientId > 0L
                && openedPatientId.equals(activePatientId)
                && openedPatientId.equals(candidatePatientId)
                && candidateMedicineId == null;
    }

    static boolean canModify(Long openedPatientId, Long activePatientId,
                             Long candidatePatientId, Long candidateMedicineId,
                             Long persistedPatientId, Long persistedMedicineId) {
        return openedPatientId != null && openedPatientId > 0L
                && openedPatientId.equals(activePatientId)
                && openedPatientId.equals(candidatePatientId)
                && openedPatientId.equals(persistedPatientId)
                && candidateMedicineId != null && candidateMedicineId > 0L
                && candidateMedicineId.equals(persistedMedicineId);
    }
}
