/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.healthcareprovider.remote;

import okhttp3.HttpUrl;

/**
 * Produces a diagnostic network target that cannot expose URL paths, queries or fragments.
 */
final class NetworkLogSanitizer {

    private NetworkLogSanitizer() {
    }

    static String origin(HttpUrl url) {
        if (url == null) {
            return "<unknown>";
        }

        StringBuilder builder = new StringBuilder()
                .append(url.scheme())
                .append("://")
                .append(url.host());

        int port = url.port();
        if (!isDefaultPort(url.scheme(), port)) {
            builder.append(':').append(port);
        }
        return builder.toString();
    }

    private static boolean isDefaultPort(String scheme, int port) {
        return ("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443);
    }
}
