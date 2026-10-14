/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.allergies;

/** Pure validation: incomplete catalog lookups cannot mean "no allergy". */
final class AllergenCatalogGuard {
    private AllergenCatalogGuard() {
    }

    static boolean validPrescriptionCode(String code) {
        return code != null && !code.trim().isEmpty();
    }

    static boolean uniqueCatalogMatch(int count) {
        return count == 1;
    }
}
