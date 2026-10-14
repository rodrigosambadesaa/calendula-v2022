/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

/**
 * Bound the memory used to render the first page of a provider PDF.
 * Page sizes are untrusted; calculating their product as an int may overflow.
 */
final class PdfBitmapBounds {
    static final int MAX_SIDE = 3072;
    static final long MAX_PIXELS = 4_000_000L;

    private PdfBitmapBounds() {
    }

    static int[] fit(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("PDF page dimensions must be positive");
        }
        double scale = Math.min(2.0d,
                Math.min((double) MAX_SIDE / width, (double) MAX_SIDE / height));
        scale = Math.min(scale, Math.sqrt((double) MAX_PIXELS
                / ((double) width * (double) height)));
        if (!(scale > 0.0d) || Double.isNaN(scale) || Double.isInfinite(scale)) {
            throw new IllegalArgumentException("Cannot calculate a safe PDF preview");
        }
        int outputWidth = Math.max(1, (int) Math.floor(width * scale));
        int outputHeight = Math.max(1, (int) Math.floor(height * scale));
        return new int[]{outputWidth, outputHeight};
    }
}
