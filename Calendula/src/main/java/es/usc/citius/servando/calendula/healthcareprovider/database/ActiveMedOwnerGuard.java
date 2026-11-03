/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.healthcareprovider.database;

/** Pure patient and linked-provider identity checks. */
public final class ActiveMedOwnerGuard {
    private ActiveMedOwnerGuard() {
    }

    public static boolean samePatient(Long storedOwnerId, Long activeOwnerId) {
        return storedOwnerId != null && storedOwnerId > 0L
                && storedOwnerId.equals(activeOwnerId);
    }

    /**
     * A cached Patient object must not authorize provider treatment for a
     * different profile that inherited the same SQLite ID after restore.
     * No stable identity: no provider medication lookup.
     */
    public static boolean samePersistedPatient(
            es.usc.citius.servando.calendula.persistence.Patient requested,
            es.usc.citius.servando.calendula.persistence.Patient persisted) {
        if (requested == null || persisted == null
                || !samePatient(requested.getId(), persisted.getId())) {
            return false;
        }
        String requestedCode = requested.getCode();
        return requestedCode != null && !requestedCode.trim().isEmpty()
                && requestedCode.equals(persisted.getCode());
    }

    public static boolean sameProviderLink(Long localProviderId, Long selectedProviderId) {
        return localProviderId != null && localProviderId > 0L
                && localProviderId.equals(selectedProviderId);
    }
}
