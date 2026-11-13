/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

/** JVM-testable patient and persisted-medicine identity policy. */
final class MedicineDetailsOwnerGuard {
    private MedicineDetailsOwnerGuard() {
    }

    static boolean matches(Long openedPatientId, Long activePatientId,
                           Long selectedMedicineId, Long selectedOwnerId,
                           Long persistedMedicineId, Long persistedOwnerId) {
        return openedPatientId != null && openedPatientId > 0L
                && openedPatientId.equals(activePatientId)
                && selectedMedicineId != null && selectedMedicineId > 0L
                && selectedMedicineId.equals(persistedMedicineId)
                && openedPatientId.equals(selectedOwnerId)
                && openedPatientId.equals(persistedOwnerId);
    }
    /**
     * Ignore unrelated or stale model notifications. A medication row ID can
     * also be recycled, so both event and displayed owner codes must match.
     */
    static boolean sameMedicineUpdate(Long displayedMedicineId,
                                      Long displayedPatientId, String displayedPatientCode,
                                      Long eventMedicineId,
                                      Long eventPatientId, String eventPatientCode) {
        return displayedMedicineId != null && displayedMedicineId > 0L
                && displayedMedicineId.equals(eventMedicineId)
                && displayedPatientId != null && displayedPatientId > 0L
                && displayedPatientId.equals(eventPatientId)
                && displayedPatientCode != null && !displayedPatientCode.trim().isEmpty()
                && displayedPatientCode.equals(eventPatientCode);
    }

}
