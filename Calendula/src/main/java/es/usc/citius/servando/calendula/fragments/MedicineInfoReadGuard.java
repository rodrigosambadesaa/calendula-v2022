/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

/** Pure clinical display policy, shared by the stock card and owner checks. */
final class MedicineInfoReadGuard {
    private MedicineInfoReadGuard() {
    }

    static boolean isCurrentOwner(Long medicineId, Long selectedOwnerId,
                                  Long storedMedicineId, Long storedOwnerId,
                                  Long activeOwnerId) {
        return medicineId != null && medicineId > 0L
                && selectedOwnerId != null && selectedOwnerId > 0L
                && medicineId.equals(storedMedicineId)
                && selectedOwnerId.equals(storedOwnerId)
                && selectedOwnerId.equals(activeOwnerId);
    }

    static boolean isUsableStock(Float value) {
        return value != null && !Float.isNaN(value) && !Float.isInfinite(value)
                && value >= 0f;
    }

    static String formatStock(Float value) {
        if (!isUsableStock(value)) {
            return null;
        }
        float quantity = value;
        if (quantity <= Integer.MAX_VALUE && quantity == (float) (int) quantity) {
            return Integer.toString((int) quantity);
        }
        return Float.toString(quantity);
    }
}
