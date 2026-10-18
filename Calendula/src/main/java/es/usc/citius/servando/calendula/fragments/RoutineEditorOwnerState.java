/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

import android.os.Bundle;

import es.usc.citius.servando.calendula.persistence.Patient;

/** A restored routine form may only operate on the patient that opened it. */
final class RoutineEditorOwnerState {
    static final String ORIGINAL_OWNER = "routine_editor_original_patient";

    private RoutineEditorOwnerState() { }

    static Long restoredOwnerOrNull(Bundle restoredState, Patient active) {
        if (active == null || active.getId() == null || active.getId() <= 0L) {
            return null;
        }
        if (restoredState == null) {
            return active.getId();
        }
        try {
            Object saved = restoredState.get(ORIGINAL_OWNER);
            return saved instanceof Long && ((Long) saved) > 0L
                    && saved.equals(active.getId()) ? (Long) saved : null;
        } catch (RuntimeException malformedState) {
            return null;
        }
    }

    static boolean stillOwnedBy(Long originalId, Patient active) {
        return originalId != null && originalId > 0L
                && active != null && originalId.equals(active.getId());
    }
}
