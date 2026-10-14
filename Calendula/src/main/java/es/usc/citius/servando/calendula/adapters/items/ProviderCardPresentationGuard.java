/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.adapters.items;

/** Pure rules for safe rendering of incomplete external dosage snapshots. */
final class ProviderCardPresentationGuard {
    private ProviderCardPresentationGuard() {
    }

    static boolean hasFirstDosageEntry(int count) {
        return count > 0;
    }

    static boolean mayShowUpdateDate(Long updatedAtMs, long nowMs, boolean updateSeen) {
        if (updatedAtMs == null || updatedAtMs < 0 || nowMs < updatedAtMs) {
            return false;
        }
        return !updateSeen || nowMs - updatedAtMs <= 3L * 24 * 60 * 60 * 1000;
    }
}
