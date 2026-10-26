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

    /** Never display incomplete or non-finite provider repeat intervals as a real dose. */
    static boolean canRenderPeriod(Double value, org.hl7.fhir.dstu3.model.Timing.UnitsOfTime units) {
        if (value == null || value <= 0.0d || Double.isNaN(value)
                || Double.isInfinite(value) || units == null) {
            return false;
        }
        switch (units) {
            case S:
            case MIN:
            case H:
            case D:
            case WK:
            case MO:
            case A:
                return true;
            default:
                return false;
        }
    }

    static boolean mayShowUpdateDate(Long updatedAtMs, long nowMs, boolean updateSeen) {
        if (updatedAtMs == null || updatedAtMs < 0 || nowMs < updatedAtMs) {
            return false;
        }
        return !updateSeen || nowMs - updatedAtMs <= 3L * 24 * 60 * 60 * 1000;
    }
}
