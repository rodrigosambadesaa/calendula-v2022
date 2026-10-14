/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments.dosePickers;

/** Strict, locale-tolerant entry parser for a manually selected clinical dose. */
final class DoseInputGuard {
    private DoseInputGuard() {
    }

    /** All graphical dose pickers must reject missing, zero and non-finite quantities. */
    static boolean isPositiveFinite(double dose) {
        return dose > 0.0d && !Double.isNaN(dose) && !Double.isInfinite(dose);
    }

    static Double parsePositive(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return null;
        }
        boolean hasDigits = false;
        boolean hasSeparator = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                hasDigits = true;
            } else if (c == '.' || c == ',') {
                if (hasSeparator) {
                    return null;
                }
                hasSeparator = true;
            } else {
                // Do not accept exponents, signs, whitespace inside a number,
                // NaN or Infinity copied from another application.
                return null;
            }
        }
        if (!hasDigits) {
            return null;
        }
        try {
            double dose = Double.parseDouble(text.replace(',', '.'));
            return isPositiveFinite(dose) ? dose : null;
        } catch (NumberFormatException invalid) {
            return null;
        }
    }
}
