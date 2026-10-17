/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import android.content.Intent;
import android.os.Bundle;

/** Prevent deferred medication catalog results from crossing patient profiles. */
final class MedicineSearchOwnerGuard {
    static final String SAVED_OWNER_KEY = "med_search_original_patient";
    private static final int MAX_INBOUND_TEXT_LENGTH = 4096;

    private MedicineSearchOwnerGuard() { }

    /**
     * A restored activity must carry the originally selected patient. If this
     * key is missing, malformed or wrong-typed, never silently substitute the
     * currently active patient after a process recreation or profile change.
     */
    static Long restoredOwnerOrNull(Bundle savedState, Long initialOwner) {
        if (savedState == null) {
            return initialOwner;
        }
        try {
            if (!savedState.containsKey(SAVED_OWNER_KEY)) {
                return null;
            }
            long restored = savedState.getLong(SAVED_OWNER_KEY, -1L);
            return restored > 0L ? restored : null;
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    /** Reject wrong-typed, corrupt or excessively large inbound Intent extras. */
    static String safeStringExtra(Intent source, String key) {
        if (source == null || key == null) {
            return null;
        }
        try {
            String value = source.getStringExtra(key);
            return value != null && value.length() <= MAX_INBOUND_TEXT_LENGTH
                    ? value : null;
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    static boolean samePatient(Long openedPatientId, Long currentPatientId) {
        return openedPatientId != null && openedPatientId > 0L
                && openedPatientId.equals(currentPatientId);
    }
}
