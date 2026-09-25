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
