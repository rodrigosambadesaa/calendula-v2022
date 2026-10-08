/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.drugdb.cima;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;

import es.usc.citius.servando.calendula.util.NetworkUtils;

/**
 * Official CIMA read-only transport. Does not write to the medicine database.
 *
 * TLS certificate validation is supplied by the Android platform. Deliberately
 * refuse redirects rather than following a redirect to an unaudited origin.
 * A future importer needs separate reconciliation and transactional validation.
 */
public final class CimaRestClient {

    static final int MAX_RESPONSE_BYTES = 256 * 1024;
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 8000;

    private CimaRestClient() {
    }

    /**
     * May block; must be called only on a worker thread, never the UI thread.
     */
    public static CimaRestCatalog.MedicineSnapshot fetchMedicine(
            Context context, String registrationNumber) throws IOException {
        if (context == null) {
            throw new IllegalArgumentException("Context is required");
        }
        String url = CimaRestCatalog.detailUrl(registrationNumber);
        if (!NetworkUtils.isBackendAvailable(context, url)) {
            throw new IOException("CIMA unavailable via the current network");
        }

        // CimaRestCatalog only constructs official HTTPS URLs with numeric IDs.
        // No caller-supplied host, protocol, userinfo or query suffix is accepted.
        HttpsURLConnection connection =
                (HttpsURLConnection) new URL(url).openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Accept-Encoding", "identity");

            return parseResponse(connection, registrationNumber);
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Validate a single already-open CIMA HTTP response. Kept separate from the
     * transport to verify error paths offline with a controlled connection.
     * Never perform a redirect, retry or write to the database here.
     */
    static CimaRestCatalog.MedicineSnapshot parseResponse(
            HttpURLConnection connection, String registrationNumber) throws IOException {
        int status = connection.getResponseCode();
        if (!isExpectedJsonResponse(status, connection.getContentType())) {
            throw new IOException(
                    "Unexpected CIMA response status or content type: " + status);
        }
        // getContentLengthLong() requires Android API 24; getContentLength()
        // works on API 23. Unknown/overflowing header lengths are still
        // constrained by the streamed byte limit below.
        if (connection.getContentLength() > MAX_RESPONSE_BYTES) {
            throw new IOException("CIMA response exceeds maximum size");
        }

        final String json;
        try (InputStream input = connection.getInputStream()) {
            json = readBoundedUtf8(input, MAX_RESPONSE_BYTES);
        }
        try {
            // Never associate the details of medicine A with a request for B.
            return CimaRestCatalog.parseMedicine(json, registrationNumber);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid CIMA medicine data", e);
        }
    }

    static boolean isExpectedJsonResponse(int status, String contentType) {
        if (status != HttpURLConnection.HTTP_OK || contentType == null) {
            return false;
        }
        String mime = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return "application/json".equals(mime);
    }

    static String readBoundedUtf8(InputStream input, int limit) throws IOException {
        if (input == null || limit < 0) {
            throw new IllegalArgumentException("Missing response or invalid byte limit");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream(
                Math.min(limit, 8192));
        byte[] buffer = new byte[8192];
        int count;
        int total = 0;
        while ((count = input.read(buffer)) != -1) {
            if (count > limit - total) {
                throw new IOException("CIMA response exceeds maximum size");
            }
            output.write(buffer, 0, count);
            total += count;
        }
        // String(byte[], UTF_8) silently inserts U+FFFD for corrupted bytes.
        // Reject malformed source data rather than importing an altered
        // medicine label or presentation name.
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(output.toByteArray()))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new IOException("CIMA returned malformed UTF-8", e);
        }
    }
}
