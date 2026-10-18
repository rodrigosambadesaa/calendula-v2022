/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import android.webkit.WebSettings;

/**
 * Device-private and mixed-content resources must never be reachable from a
 * remote or cached medication document, regardless of the selected cache mode.
 *
 * This is applied before any HTML, JavaScript bridge or URL is loaded.
 */
final class WebViewAccessPolicy {
    private WebViewAccessPolicy() {
    }

    @SuppressWarnings("deprecation")
    static void harden(WebSettings settings) {
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    }
}
