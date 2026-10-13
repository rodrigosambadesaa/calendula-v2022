/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

/** JVM-testable patient isolation rule for medicine-specific alert screens. */
final class ClinicalAlertOwnerGuard {
    private ClinicalAlertOwnerGuard() {
    }

    static boolean isCurrent(Long expectedMedicineId, Long selectedOwnerId,
                             Long currentMedicineId, Long persistedOwnerId,
                             Long activeOwnerId) {
        return expectedMedicineId != null && expectedMedicineId > 0L
                && selectedOwnerId != null && selectedOwnerId > 0L
                && expectedMedicineId.equals(currentMedicineId)
                && selectedOwnerId.equals(persistedOwnerId)
                && selectedOwnerId.equals(activeOwnerId);
    }
}
