/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util;

import es.usc.citius.servando.calendula.persistence.Patient;

/**
 * A recycled SQLite patient row ID must never authorize previously cached
 * clinical list items, stale editor routes or background query results.
 * An unidentified legacy local profile needs persisted identity migration
 * before a clinical UI may claim ownership.
 */
public final class PatientOwnerGuard {
    private PatientOwnerGuard() {
    }

    public static boolean hasStableIdentity(Patient patient) {
        return patient != null && patient.getId() != null && patient.getId() > 0L
                && patient.getCode() != null && !patient.getCode().trim().isEmpty();
    }

    public static boolean sameStableIdentity(Patient expected, Patient current) {
        return hasStableIdentity(expected) && hasStableIdentity(current)
                && expected.getId().equals(current.getId())
                && expected.getCode().equals(current.getCode());
    }
}
