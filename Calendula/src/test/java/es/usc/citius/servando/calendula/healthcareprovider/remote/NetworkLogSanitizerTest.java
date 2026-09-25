/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.healthcareprovider.remote;

import org.junit.Test;

import okhttp3.HttpUrl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class NetworkLogSanitizerTest {

    @Test
    public void originRemovesSensitivePathQueryAndFragment() {
        HttpUrl url = HttpUrl.parse(
                "https://api.example.test/patients/123/medication?iid=secret-token#details");

        String safe = NetworkLogSanitizer.origin(url);

        assertEquals("https://api.example.test", safe);
        assertFalse(safe.contains("patients"));
        assertFalse(safe.contains("123"));
        assertFalse(safe.contains("secret-token"));
        assertFalse(safe.contains("details"));
    }

    @Test
    public void originKeepsNonDefaultPortForDiagnostics() {
        HttpUrl url = HttpUrl.parse("https://192.168.2.124:4676/dispensation?iid=secret");

        assertEquals("https://192.168.2.124:4676", NetworkLogSanitizer.origin(url));
    }

    @Test
    public void originOmitsDefaultPorts() {
        assertEquals(
                "https://api.example.test",
                NetworkLogSanitizer.origin(HttpUrl.parse("https://api.example.test:443/path")));
        assertEquals(
                "http://api.example.test",
                NetworkLogSanitizer.origin(HttpUrl.parse("http://api.example.test:80/path")));
    }

    @Test
    public void nullUrlHasSafePlaceholder() {
        assertEquals("<unknown>", NetworkLogSanitizer.origin(null));
    }
}
