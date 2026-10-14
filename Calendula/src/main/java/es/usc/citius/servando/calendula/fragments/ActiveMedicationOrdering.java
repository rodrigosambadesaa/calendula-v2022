/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

/** Stable ordering of provider medicines, including incomplete legacy rows. */
final class ActiveMedicationOrdering {
    private ActiveMedicationOrdering() {
    }

    static int compare(boolean firstInactive, boolean secondInactive,
                       Long firstUpdatedMs, Long secondUpdatedMs) {
        if (firstInactive != secondInactive) {
            return firstInactive ? 1 : -1;
        }
        if (firstUpdatedMs == secondUpdatedMs) {
            return 0;
        }
        if (firstUpdatedMs == null) {
            return 1;
        }
        if (secondUpdatedMs == null) {
            return -1;
        }
        return Long.compare(firstUpdatedMs, secondUpdatedMs);
    }
}
