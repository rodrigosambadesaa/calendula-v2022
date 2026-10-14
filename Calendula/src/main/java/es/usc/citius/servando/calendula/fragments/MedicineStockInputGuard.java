/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

import java.math.BigDecimal;

/** Prevent manual stock edits from persisting invalid clinical quantities. */
final class MedicineStockInputGuard {
    private MedicineStockInputGuard() {
    }

    static boolean usableStock(float stock) {
        return !Float.isNaN(stock) && !Float.isInfinite(stock) && stock >= 0f;
    }

    /**
     * @return updated quantity, or null to reject invalid/unrepresentable input.
     * The -1 sentinel means unmanaged stock, NOT zero existing units.
     */
    static Float adjust(float currentStock, BigDecimal input, boolean adding) {
        if (input == null || input.signum() <= 0) {
            return null;
        }
        final float amount = input.floatValue();
        if (!usableStock(amount) || amount == 0f) {
            return null; // overflow or positive BigDecimal underflow to zero
        }
        if (adding) {
            if (currentStock != -1f && !usableStock(currentStock)) {
                return null;
            }
            final float base = currentStock == -1f ? 0f : currentStock;
            final float sum = base + amount;
            // Reject additions too small for float precision at the current
            // magnitude, rather than claiming to have added units when the
            // persisted quantity would be bit-for-bit unchanged.
            return usableStock(sum) && sum > base ? sum : null;
        }
        if (!usableStock(currentStock)) {
            // Subtracting from an unknown stock must not manufacture a
            // misleading "out of stock" quantity.
            return null;
        }
        if (amount >= currentStock) {
            return 0f;
        }
        final float remaining = currentStock - amount;
        return usableStock(remaining) && remaining < currentStock ? remaining : null;
    }
}
