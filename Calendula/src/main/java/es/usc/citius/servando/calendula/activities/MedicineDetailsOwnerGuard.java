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
}
