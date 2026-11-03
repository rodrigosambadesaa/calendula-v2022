/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Small, side-effect-free policy for deciding which top-level URLs may stay inside the
 * in-app WebView.
 *
 * The original implementation used String.contains(), which allowed an unrelated origin
 * to look "internal" merely by embedding the trusted URL in its path, query or user-info.
 */
final class WebViewNavigationPolicy {

    private WebViewNavigationPolicy() {
    }

    static boolean shouldOpenInsideWebView(
            String initialUrl,
            String targetUrl,
            boolean externalLinksEnabled) {
        if (!isRemoteHttpUrl(targetUrl)) {
            return false;
        }
        return externalLinksEnabled || isSameOrigin(initialUrl, targetUrl);
    }

    static boolean isSameOrigin(String firstUrl, String secondUrl) {
        URI first = parseRemoteHttpUri(firstUrl);
        URI second = parseRemoteHttpUri(secondUrl);
        if (first == null || second == null) {
            return false;
        }

        return first.getScheme().equalsIgnoreCase(second.getScheme())
                && first.getHost().equalsIgnoreCase(second.getHost())
                && effectivePort(first) == effectivePort(second);
    }

    static boolean isRemoteHttpUrl(String targetUrl) {
        return parseRemoteHttpUri(targetUrl) != null;
    }

    /**
     * Initial requests come from persisted intents and catalog lookups.
     * Never allow local filesystem, script, relative or credential-bearing
     * URLs to enter a privileged WebView that may inject CSS or cache HTML.
     */
    static boolean isSafeInitialDocumentUrl(String targetUrl) {
        URI uri = parseRemoteHttpUri(targetUrl);
        return uri != null && uri.getUserInfo() == null
                && uri.getHost() != null && !uri.getHost().isEmpty()
                && uri.getPort() <= 65535;
    }

    static boolean isWebViewLocalUrl(String targetUrl) {
        if (targetUrl == null) {
            return false;
        }
        return startsWithScheme(targetUrl, "about:")
                || startsWithScheme(targetUrl, "data:")
                || startsWithScheme(targetUrl, "javascript:");
    }

    private static URI parseRemoteHttpUri(String value) {
        if (value == null) {
            return null;
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return null;
            }
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return null;
            }
            // Apply the same restriction to redirected and clicked links as
            // to the original clinical document URL. A trusted hostname
            // with user-info can still embed credentials in a navigation.
            // Port 0 and ports outside TCP's range are not remote endpoints.
            if (uri.getRawUserInfo() != null || uri.getPort() == 0
                    || uri.getPort() > 65535) {
                return null;
            }
            return uri;
        } catch (URISyntaxException ignored) {
            return null;
        }
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static boolean startsWithScheme(String value, String prefix) {
        return value.regionMatches(true, 0, prefix, 0, prefix.length());
    }
}
