/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

/** Pure policy for absent external healthcare-provider medication records. */
final class MedicineInfoAvailabilityGuard {
    private MedicineInfoAvailabilityGuard() {
    }

    /** A provider-only request cannot render a null or deleted provider entry. */
    static boolean canShowRequestedProvider(boolean providerOnlyRequested,
                                            boolean providerRecordExists) {
        return !providerOnlyRequested || providerRecordExists;
    }

    /** Show local medicine information regardless of lost provider linkage. */
    static boolean showLinkedProviderTab(boolean localMedicineExists,
                                         boolean providerRecordExists) {
        return localMedicineExists && providerRecordExists;
    }
}
