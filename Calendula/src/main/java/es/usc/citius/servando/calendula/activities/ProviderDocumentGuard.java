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

    /**
     * Clinical WebViews must identify the original *profile*, not just its
     * SQLite row ID. Restores/imports can reuse numeric IDs for another user.
     * An unpopulated code is not a trustworthy stable owner identity.
     */
    static boolean samePatient(Long expectedId, String expectedCode,
                               Long activeId, String activeCode) {
        return samePatient(expectedId, activeId)
                && expectedCode != null && !expectedCode.trim().isEmpty()
                && expectedCode.equals(activeCode);
    }

    static boolean canAcceptResponse(Long opened, Long active, Long responseOwner) {
        return samePatient(opened, active) && samePatient(opened, responseOwner);
    }

    static boolean canAcceptResponse(Long opened, Long active, Long responseOwner,
                                     int expectedJobId, int responseJobId) {
        return canAcceptResponse(opened, active, responseOwner)
                && expectedJobId > 0 && expectedJobId == responseJobId;
    }

    /** Apply the stable profile identity gate before accepting a PDF job reply. */
    static boolean canAcceptResponse(Long openedId, String openedCode,
                                     Long activeId, String activeCode,
                                     Long responseOwner, int expectedJobId,
                                     int responseJobId) {
        return samePatient(openedId, openedCode, activeId, activeCode)
                && canAcceptResponse(openedId, activeId, responseOwner,
                        expectedJobId, responseJobId);
    }
}
