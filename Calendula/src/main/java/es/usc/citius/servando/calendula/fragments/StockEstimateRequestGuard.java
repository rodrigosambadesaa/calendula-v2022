/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

/**
 * Accept an asynchronous stock estimate only for its originating editor state.
 * No Android/SQLite references: deterministic unit tests can exercise races.
 */
final class StockEstimateRequestGuard {
    private StockEstimateRequestGuard() {
    }

    static boolean isCurrent(long requestVersion, long currentVersion,
                             Object requestedMedicine, Object currentMedicine,
                             float requestedStock, float currentStock,
                             boolean stockEnabled) {
        return stockEnabled
                && requestVersion == currentVersion
                && requestedMedicine != null
                && requestedMedicine == currentMedicine
                && Float.isFinite(requestedStock)
                && requestedStock >= 0f
                && Float.compare(requestedStock, currentStock) == 0;
    }
}
