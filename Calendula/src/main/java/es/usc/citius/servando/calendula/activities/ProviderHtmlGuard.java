/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

/**
 * Fail-closed entry gate for HTML provided to the embedded medication leaflet
 * viewer. Sanitizer failure is NOT permission to render the raw source.
 */
final class ProviderHtmlGuard {
    static final int MAX_HTML_CHARS = 2_000_000;

    interface HtmlTransformer {
        String sanitize(String html);
    }

    private ProviderHtmlGuard() {
    }

    static boolean isBoundedHtml(String html) {
        return html != null && !html.isEmpty() && html.length() <= MAX_HTML_CHARS;
    }

    static String processOrNull(String html, HtmlTransformer processor) {
        if (!isBoundedHtml(html) || processor == null) {
            return null;
        }
        try {
            String output = processor.sanitize(html);
            return isBoundedHtml(output) ? output : null;
        } catch (RuntimeException invalidDocument) {
            return null;
        }
    }
}
