/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util;

import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HttpDownloadUtilTest {

    @Test
    public void readLimitedTextReturnsContentWithinLimit() throws Exception {
        assertEquals(
                "{\"AEMPS\":{}}",
                HttpDownloadUtil.readLimitedText(
                        new StringReader("{\"AEMPS\":{}}"),
                        64));
    }

    @Test(expected = IOException.class)
    public void readLimitedTextRejectsContentPastLimit() throws Exception {
        HttpDownloadUtil.readLimitedText(new StringReader("123456"), 5);
    }

    @Test
    public void readLimitedTextAllowsExactLimit() throws Exception {
        assertEquals("12345", HttpDownloadUtil.readLimitedText(new StringReader("12345"), 5));
    }

    @Test(expected = IllegalArgumentException.class)
    public void readLimitedTextRejectsNegativeLimit() throws Exception {
        HttpDownloadUtil.readLimitedText(new StringReader(""), -1);
    }

    @Test
    public void redirectCodesIncludePermanentTemporarySeeOtherAnd307308() {
        assertTrue(HttpDownloadUtil.isRedirectCode(301));
        assertTrue(HttpDownloadUtil.isRedirectCode(302));
        assertTrue(HttpDownloadUtil.isRedirectCode(303));
        assertTrue(HttpDownloadUtil.isRedirectCode(307));
        assertTrue(HttpDownloadUtil.isRedirectCode(308));
        assertFalse(HttpDownloadUtil.isRedirectCode(300));
        assertFalse(HttpDownloadUtil.isRedirectCode(304));
        assertFalse(HttpDownloadUtil.isRedirectCode(200));
    }

    @Test
    public void relativeRedirectIsResolvedAgainstCurrentBackend() throws Exception {
        assertEquals(
                "https://example.test/dbs/versions.json",
                HttpDownloadUtil.resolveRedirectUrl(
                        "https://example.test/download/current.json",
                        "../dbs/versions.json"));
    }

    @Test
    public void absoluteRedirectMayChangeHostBeforeNextPreflight() throws Exception {
        assertEquals(
                "https://cdn.example.test/db/archive.zip",
                HttpDownloadUtil.resolveRedirectUrl(
                        "https://example.test/db/archive.zip",
                        "https://cdn.example.test/db/archive.zip"));
    }

    @Test
    public void httpsToHttpRedirectIsRejectedAsDowngrade() throws Exception {
        assertTrue(HttpDownloadUtil.isHttpsDowngrade(
                "https://example.test/db/archive.zip",
                "http://example.test/db/archive.zip"));
        assertFalse(HttpDownloadUtil.isHttpsDowngrade(
                "http://example.test/db/archive.zip",
                "https://example.test/db/archive.zip"));
        assertFalse(HttpDownloadUtil.isHttpsDowngrade(
                "https://example.test/db/archive.zip",
                "https://cdn.example.test/db/archive.zip"));
    }
}
