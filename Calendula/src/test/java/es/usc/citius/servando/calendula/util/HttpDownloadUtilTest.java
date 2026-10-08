/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util;

import org.junit.Test;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
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
    public void httpAndHttpsBackendUrlsAreAccepted() throws Exception {
        assertEquals("http", HttpDownloadUtil.requireHttpUrl(
                "http://example.test/db/archive.zip").getProtocol());
        assertEquals("https", HttpDownloadUtil.requireHttpUrl(
                "https://example.test/db/archive.zip").getProtocol());
    }

    @Test(expected = IOException.class)
    public void missingBackendUrlIsRejected() throws Exception {
        HttpDownloadUtil.requireHttpUrl(null);
    }

    @Test(expected = IOException.class)
    public void nonHttpBackendUrlIsRejected() throws Exception {
        HttpDownloadUtil.requireHttpUrl("file:///tmp/archive.zip");
    }

    @Test(expected = IOException.class)
    public void redirectToNonHttpSchemeIsRejected() throws Exception {
        HttpDownloadUtil.resolveRedirectUrl(
                "https://example.test/db/archive.zip",
                "jar:https://example.test/archive.jar!/db.zip");
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
    @Test
    public void binaryStreamAllowsExactByteLimit() throws Exception {
        byte[] input = "12345".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertEquals(5L, HttpDownloadUtil.copyLimited(
                new ByteArrayInputStream(input), output, 5L));
        assertEquals("12345", new String(output.toByteArray(), StandardCharsets.UTF_8));
    }

    @Test(expected = IOException.class)
    public void binaryStreamRejectsUnknownLengthBeyondBudget() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        HttpDownloadUtil.copyLimited(
                new ByteArrayInputStream("123456".getBytes(StandardCharsets.UTF_8)),
                output, 5L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void binaryStreamRejectsNegativeLimit() throws Exception {
        HttpDownloadUtil.copyLimited(
                new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream(), -1L);
    }

    @org.junit.Rule
    public org.junit.rules.TemporaryFolder stagingFolder =
            new org.junit.rules.TemporaryFolder();

    @Test
    public void truncatedDownloadPreservesPriorArchiveAndRemovesStaging() throws Exception {
        java.io.File file = stagingFolder.newFile("medicine.db");
        java.nio.file.Files.write(file.toPath(), "prior".getBytes(StandardCharsets.UTF_8));
        try {
            HttpDownloadUtil.writeCompleteDownload(
                    new ByteArrayInputStream("partial".getBytes(StandardCharsets.UTF_8)),
                    file, 12L);
            org.junit.Assert.fail("Truncated download should fail");
        } catch (IOException expected) {
            // Deliberately keep the previously completed archive.
        }
        assertEquals("prior", new String(
                java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        assertEquals(1, stagingFolder.getRoot().listFiles().length);
    }

    @Test
    public void completeDownloadReplacesArchiveAndCleansUpTempFile() throws Exception {
        java.io.File file = stagingFolder.newFile("medicine.db");
        java.nio.file.Files.write(file.toPath(), "prior".getBytes(StandardCharsets.UTF_8));
        HttpDownloadUtil.writeCompleteDownload(
                new ByteArrayInputStream("replacement".getBytes(StandardCharsets.UTF_8)),
                file, 11L);
        assertEquals("replacement", new String(
                java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        assertEquals(1, stagingFolder.getRoot().listFiles().length);
    }

    @Test
    public void emptyOrUnknownLengthDownloadIsHandledSafely() throws Exception {
        java.io.File file = stagingFolder.newFile("medicine.db");
        java.nio.file.Files.write(file.toPath(), "prior".getBytes(StandardCharsets.UTF_8));
        try {
            HttpDownloadUtil.writeCompleteDownload(
                    new ByteArrayInputStream(new byte[0]), file, -1L);
            org.junit.Assert.fail("Empty archive should fail");
        } catch (IOException expected) {
            // The old archive must not be erased.
        }
        assertEquals("prior", new String(
                java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        HttpDownloadUtil.writeCompleteDownload(
                new ByteArrayInputStream("new".getBytes(StandardCharsets.UTF_8)),
                file, -1L);
        assertEquals("new", new String(
                java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void interruptedStreamLeavesOldArchiveUntouched() throws Exception {
        java.io.File file = stagingFolder.newFile("medicine.db");
        java.nio.file.Files.write(file.toPath(), "prior".getBytes(StandardCharsets.UTF_8));
        java.io.InputStream interrupted = new java.io.InputStream() {
            int reads = 0;
            @Override
            public int read() throws IOException {
                throw new IOException("interrupted");
            }
            @Override
            public int read(byte[] data, int offset, int length) throws IOException {
                if (++reads > 1) {
                    throw new IOException("interrupted");
                }
                data[offset] = 'x';
                return 1;
            }
        };
        try {
            HttpDownloadUtil.writeCompleteDownload(interrupted, file, -1L);
            org.junit.Assert.fail("Mid-transfer I/O error should fail");
        } catch (IOException expected) {
            // The staging file must be removed on I/O error.
        }
        assertEquals("prior", new String(
                java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        assertEquals(1, stagingFolder.getRoot().listFiles().length);
    }


}
