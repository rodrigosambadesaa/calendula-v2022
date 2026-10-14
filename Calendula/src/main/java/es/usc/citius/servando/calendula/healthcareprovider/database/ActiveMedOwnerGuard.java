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

    public static boolean sameProviderLink(Long localProviderId, Long selectedProviderId) {
        return localProviderId != null && localProviderId > 0L
                && localProviderId.equals(selectedProviderId);
    }
}
