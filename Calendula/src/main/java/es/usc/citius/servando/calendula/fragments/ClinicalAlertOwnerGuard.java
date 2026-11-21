/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.util.PatientOwnerGuard;

/** JVM-testable patient isolation rule for medicine-specific alert screens. */
final class ClinicalAlertOwnerGuard {
    private ClinicalAlertOwnerGuard() {
    }

    /**
     * A restored patient can inherit another account's numeric SQLite ID.
     * An alert screen must validate the originally displayed patient against
     * the current medicine, selected profile AND the actual persisted owner.
     */
    static boolean isCurrentStable(Long expectedMedicineId, Patient openedOwner,
                                   Long persistedMedicineId, Patient medicineOwner,
                                   Patient activeOwner, Patient liveOwner) {
        return openedOwner != null
                && medicineOwner != null
                && activeOwner != null
                && liveOwner != null
                && isCurrent(expectedMedicineId, openedOwner.getId(),
                        persistedMedicineId, medicineOwner.getId(), activeOwner.getId())
                && PatientOwnerGuard.sameStableIdentity(openedOwner, medicineOwner)
                && PatientOwnerGuard.sameStableIdentity(openedOwner, activeOwner)
                && PatientOwnerGuard.sameStableIdentity(openedOwner, liveOwner);
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
