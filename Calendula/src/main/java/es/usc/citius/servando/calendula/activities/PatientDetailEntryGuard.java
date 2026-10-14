/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

/** Validate patient detail intent IDs before opening or saving medical data. */
final class PatientDetailEntryGuard {
    private PatientDetailEntryGuard() { }

    static boolean canOpen(long requestedId, Long persistedId) {
        if (requestedId == -1L) {
            return true; // New patient profile.
        }
        return requestedId > 0L && persistedId != null
                && persistedId > 0L && persistedId == requestedId;
    }
}
