/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

/** Pure ownership checks for an asynchronously returned provider PDF. */
final class ProviderDocumentGuard {
    private ProviderDocumentGuard() {
    }

    static boolean samePatient(Long expected, Long actual) {
        return expected != null && expected > 0L && expected.equals(actual);
    }

    static boolean canAcceptResponse(Long opened, Long active, Long responseOwner) {
        return samePatient(opened, active) && samePatient(opened, responseOwner);
    }

    static boolean canAcceptResponse(Long opened, Long active, Long responseOwner,
                                     int expectedJobId, int responseJobId) {
        return canAcceptResponse(opened, active, responseOwner)
                && expectedJobId > 0 && expectedJobId == responseJobId;
    }
}
