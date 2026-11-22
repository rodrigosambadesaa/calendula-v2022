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
import java.net.HttpURLConnection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HttpDownloadUtilTest {

    @Test
    public void connectionFailureClosesPartialHttpSocket() throws Exception {
        HttpURLConnection connection = mock(HttpURLConnection.class);
        doThrow(new IOException("synthetic connect failure")).when(connection).connect();
        try {
            HttpDownloadUtil.configureAndReadStatus(connection);
            org.junit.Assert.fail("Failed connection must not be usable");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("synthetic connect failure"));
        }
        verify(connection).disconnect();
    }

    @Test
    public void responseStatusFailureClosesConnectedHttpSocket() throws Exception {
        HttpURLConnection connection = mock(HttpURLConnection.class);
        when(connection.getResponseCode()).thenThrow(
                new IOException("synthetic status failure"));
        try {
            HttpDownloadUtil.configureAndReadStatus(connection);
            org.junit.Assert.fail("Status read failure must not keep a socket alive");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("synthetic status failure"));
        }
        verify(connection).connect();
        verify(connection).disconnect();
    }

    @Test
    public void runtimeNetworkFailureAlsoDisconnectsSafely() throws Exception {
        HttpURLConnection connection = mock(HttpURLConnection.class);
        doThrow(new SecurityException("synthetic network permission loss"))
                .when(connection).connect();
        try {
            HttpDownloadUtil.configureAndReadStatus(connection);
            org.junit.Assert.fail("Android network error should propagate safely");
        } catch (SecurityException expected) {
            // The caller will fail closed and release its remaining resources.
        }
        verify(connection).disconnect();
    }

    @Test
    public void successfulHttpStatusRemainsOwnedByDownloadCaller() throws Exception {
        HttpURLConnection connection = mock(HttpURLConnection.class);
        when(connection.getResponseCode()).thenReturn(200);
        assertEquals(200, HttpDownloadUtil.configureAndReadStatus(connection));
        verify(connection).connect();
        verify(connection, never()).disconnect();
    }

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

    @Test
    public void unsafeAuthorityAndMalformedBackendUrlsAreRejectedBeforeNetworkAccess()
            throws Exception {
        String[] invalid = {
                "https://user:secret@example.test/db/archive.zip",
                "https://example.test:0/db/archive.zip",
                "https://example.test:65536/db/archive.zip",
                "https://example.test:-2/db/archive.zip",
                "https://example.test/has whitespace/archive.zip",
                "https:///db/archive.zip"
        };
        for (String candidate : invalid) {
            try {
                HttpDownloadUtil.requireHttpUrl(candidate);
                org.junit.Assert.fail("Unexpectedly accepted invalid HTTP(S) backend URL");
            } catch (IOException expected) {
                // Fail closed; URLs are not connected or logged.
            }
        }
        assertEquals(443, HttpDownloadUtil.requireHttpUrl(
                "https://example.test:443/db/archive.zip").getPort());
    }

    @Test
    public void ambiguousUriHostIsRejectedBeforeBackendPreflight() throws Exception {
        // java.net.URL recognizes a nonempty host for these inputs, but
        // java.net.URI cannot parse it as a real network host. A single
        // ambiguous authority must not reach DNS probing or openConnection.
        String[] invalid = {
                "https://exa%6dple.test/db/archive.zip",
                "https://example.test%40other.test/db/archive.zip",
                "https://under_score.example.test/db/archive.zip",
                "https://münchen.example.test/db/archive.zip"
        };
        for (String candidate : invalid) {
            try {
                HttpDownloadUtil.requireHttpUrl(candidate);
                org.junit.Assert.fail("Unexpectedly accepted ambiguous backend host");
            } catch (IOException expected) {
                // URL parsing is deterministic: no live network request.
            }
        }
    }

    @Test
    public void ordinaryDnsPunycodeAndIpv6BackendHostsRemainAllowed()
            throws Exception {
        assertEquals("EXAMPLE.TEST", HttpDownloadUtil.requireHttpUrl(
                "https://EXAMPLE.TEST:443/db/archive.zip").getHost());
        assertEquals("xn--mnchen-3ya.example", HttpDownloadUtil.requireHttpUrl(
                "https://xn--mnchen-3ya.example/db/archive.zip").getHost());
        assertEquals("[2001:db8::1]", HttpDownloadUtil.requireHttpUrl(
                "https://[2001:db8::1]/db/archive.zip").getHost());
    }

    @Test(expected = IOException.class)
    public void encodedHostInAbsoluteRedirectIsRejected() throws Exception {
        HttpDownloadUtil.resolveRedirectUrl(
                "https://example.test/db/archive.zip",
                "https://example.test%40other.test/db/archive.zip");
    }

    @Test(expected = IOException.class)
    public void encodedHostInInitialUrlIsRejectedBeforeRelativeRedirect()
            throws Exception {
        HttpDownloadUtil.resolveRedirectUrl(
                "https://exa%6dple.test/db/archive.zip",
                "../other.zip");
    }

    @Test(expected = IOException.class)
    public void credentialedAbsoluteRedirectIsRejected() throws Exception {
        HttpDownloadUtil.resolveRedirectUrl(
                "https://example.test/db/archive.zip",
                "https://user:secret@cdn.example.test/db/archive.zip");
    }

    @Test(expected = IOException.class)
    public void invalidTcpPortRedirectIsRejected() throws Exception {
        HttpDownloadUtil.resolveRedirectUrl(
                "https://example.test/db/archive.zip",
                "https://cdn.example.test:65536/db/archive.zip");
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
    public void zeroLengthBulkBinaryReadMakesProgressWithoutLosingData() throws Exception {
        byte[] bytes = "ABC".getBytes(StandardCharsets.UTF_8);
        java.io.InputStream unusual = new java.io.InputStream() {
            int pos;
            boolean zeroOnce = true;

            @Override
            public int read(byte[] buffer, int offset, int length) {
                if (zeroOnce) {
                    zeroOnce = false;
                    return 0;
                }
                if (pos == bytes.length) return -1;
                int amount = Math.min(length, bytes.length - pos);
                System.arraycopy(bytes, pos, buffer, offset, amount);
                pos += amount;
                return amount;
            }

            @Override
            public int read() {
                return pos < bytes.length ? bytes[pos++] & 0xff : -1;
            }
        };
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        assertEquals(3L, HttpDownloadUtil.copyLimited(unusual, result, 3L));
        assertEquals("ABC", new String(result.toByteArray(), StandardCharsets.UTF_8));
    }

    @Test
    public void stalledBinaryStreamFailsWithoutFabricatingNullBytes() throws Exception {
        final int[] bulkCalls = {0};
        java.io.InputStream stalled = new java.io.InputStream() {
            @Override
            public int read(byte[] buffer, int offset, int length) {
                bulkCalls[0]++;
                return 0;
            }

            @Override
            public int read() {
                throw new AssertionError(
                        "A zero-progress stream must not invent a single NUL byte");
            }
        };
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            HttpDownloadUtil.copyLimited(stalled, output,
                    HttpDownloadUtil.MAX_BINARY_DOWNLOAD_BYTES);
            org.junit.Assert.fail("Permanently stalled binary stream should fail");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("no progress"));
        }
        assertEquals(0, output.size());
        assertEquals("The detector must be bounded", 4, bulkCalls[0]);
    }

    @Test
    public void transientZeroLengthBinaryReadsDoNotLoseValidPayload() throws Exception {
        byte[] bytes = "A\\u0000B".replace("\\u0000", "\u0000")
                .getBytes(StandardCharsets.UTF_8);
        java.io.InputStream intermittent = new ByteArrayInputStream(bytes) {
            private int zeroReads = 2;
            @Override
            public synchronized int read(byte[] b, int off, int len) {
                if (zeroReads-- > 0) {
                    return 0;
                }
                return super.read(b, off, len);
            }
        };
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertEquals(bytes.length, HttpDownloadUtil.copyLimited(
                intermittent, output, bytes.length));
        org.junit.Assert.assertArrayEquals(bytes, output.toByteArray());
    }

    @Test
    public void zeroLengthBulkTextReadMakesProgressWithoutLosingCharacters()
            throws Exception {
        StringReader unusual = new StringReader("abc") {
            boolean zeroOnce = true;

            @Override
            public int read(char[] chars, int offset, int length) throws IOException {
                if (zeroOnce) {
                    zeroOnce = false;
                    return 0;
                }
                return super.read(chars, offset, length);
            }
        };
        assertEquals("abc", HttpDownloadUtil.readLimitedText(unusual, 3));
    }

    @Test(expected = IOException.class)
    public void repeatedlyStalledTextReaderFailsWithoutFabricatingNullCharacters()
            throws Exception {
        java.io.Reader stalled = new java.io.Reader() {
            @Override
            public int read(char[] chars, int offset, int length) {
                return 0;
            }

            @Override
            public void close() {
            }
        };
        HttpDownloadUtil.readLimitedText(stalled, 16);
    }

    @Test(expected = IOException.class)
    public void zeroLengthBulkReadCannotBypassTextLimit() throws Exception {
        StringReader unusual = new StringReader("ab") {
            boolean zeroOnce = true;

            @Override
            public int read(char[] chars, int offset, int length) throws IOException {
                if (zeroOnce) {
                    zeroOnce = false;
                    return 0;
                }
                return super.read(chars, offset, length);
            }
        };
        HttpDownloadUtil.readLimitedText(unusual, 1);
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
