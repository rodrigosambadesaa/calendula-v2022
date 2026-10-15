/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util;

/**
 * Read-only guard for a historic, user-selected medicine catalog.
 *
 * Restored SharedPreferences can store an old Boolean/number under what is
 * now a String setting. Invalid selection never authorizes catalog-backed
 * prescription data, a barcode lookup or an automatic catalog installation.
 */
public final class DrugCatalogSelection {
    private DrugCatalogSelection() {
    }

    /** Returns only a nonblank well-typed catalog ID; never changes settings. */
    public static String selectedOrNull() {
        final String stored;
        try {
            stored = PreferenceUtils.getString(PreferenceKeys.DRUGDB_CURRENT_DB, null);
        } catch (ClassCastException incompatible) {
            return null;
        }
        return stored == null || stored.trim().isEmpty() ? null : stored;
    }

    public static boolean isSelected(String expectedCatalogId) {
        return expectedCatalogId != null
                && !expectedCatalogId.isEmpty()
                && expectedCatalogId.equals(selectedOrNull());
    }

    /** Only a real selected catalog enables catalog-backed editor/search UI. */
    public static boolean isReady(String none, String initializing) {
        final String selected = selectedOrNull();
        return selected != null
                && !selected.equals(none)
                && !selected.equals(initializing);
    }
}
