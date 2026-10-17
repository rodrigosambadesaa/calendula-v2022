/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.database.migrationHelpers;

import java.util.Collection;

/**
 * Relinking a medicine may rewrite its display name and presentation.
 * Permit it only for an explicitly selected, still registered drug catalog.
 * Never auto-select a default catalog after a malformed preferences restore.
 */
public final class MedicineRelinkCatalogPolicy {
    private MedicineRelinkCatalogPolicy() { }

    public static String registeredIdOrNull(String selected, Collection<String> knownIds) {
        if (selected == null || selected.trim().isEmpty()
                || knownIds == null || !knownIds.contains(selected)) {
            return null;
        }
        return selected;
    }
}
