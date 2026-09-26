/*
 *    Calendula - An assistant for personal medication management.
 *    Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 *    Calendula is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this software.  If not, see <http://www.gnu.org/licenses/>.
 */

package es.usc.citius.servando.calendula.util;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;

import es.usc.citius.servando.calendula.CalendulaApp;


public class HttpDownloadUtil {

    private static final String TAG = "HttpDownloadUtil";
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 8000;
    static final int MAX_REDIRECTS = 5;
    // versions.json is tiny; cap text responses to avoid unbounded memory growth on bad servers.
    static final int MAX_TEXT_DOWNLOAD_CHARS = 256 * 1024;

    /**
     * Backwards-compatible overload. New code should pass a Context explicitly so the request
     * cannot start without the VPN-aware network/backend preflight.
     */
    @Deprecated
    public static boolean downloadFile(final String fileUrl, final File file) {
        Context context = CalendulaApp.getContext();
        return context != null && downloadFile(context, fileUrl, file);
    }

    public static boolean downloadFile(
            final Context context,
            final String fileUrl,
            final File file) {
        FileOutputStream fileOutput = null;
        InputStream inputStream = null;
        HttpURLConnection connection = null;
        try {
            connection = openBackendConnection(context, fileUrl);

            fileOutput = new FileOutputStream(file);
            inputStream = connection.getInputStream();

            byte[] buffer = new byte[1024];
            int bufferLength;

            while ((bufferLength = inputStream.read(buffer)) > 0) {
                fileOutput.write(buffer, 0, bufferLength);
            }
            return true;
        } catch (IOException e) {
            LogUtil.e(TAG, "downloadFile: ", e);
            return false;
        } finally {
            CloseableUtil.closeQuietly(fileOutput, inputStream);
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Backwards-compatible overload. New code should pass a Context explicitly.
     */
    @Deprecated
    public static String downloadFileToText(final String fileUrl) {
        Context context = CalendulaApp.getContext();
        return context != null ? downloadFileToText(context, fileUrl) : null;
    }

    public static String downloadFileToText(
            final Context context,
            final String fileUrl) {
        BufferedReader in = null;
        HttpURLConnection connection = null;
        try {
            connection = openBackendConnection(context, fileUrl);
            final int contentLength = connection.getContentLength();
            if (contentLength > MAX_TEXT_DOWNLOAD_CHARS) {
                throw new IOException("Text response exceeds maximum allowed size");
            }

            in = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
            return readLimitedText(in, MAX_TEXT_DOWNLOAD_CHARS);
        } catch (IOException e) {
            LogUtil.e(TAG, "downloadFileToText: ", e);
            return null;
        } finally {
            CloseableUtil.closeQuietly(in);
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    static String readLimitedText(Reader reader, int maxChars) throws IOException {
        if (maxChars < 0) {
            throw new IllegalArgumentException("maxChars must be >= 0");
        }

        StringBuilder sb = new StringBuilder(Math.min(maxChars, 4096));
        char[] buffer = new char[4096];
        int read;
        while ((read = reader.read(buffer)) != -1) {
            if (sb.length() + read > maxChars) {
                throw new IOException("Text response exceeds maximum allowed size");
            }
            sb.append(buffer, 0, read);
        }
        return sb.toString();
    }

    private static HttpURLConnection openBackendConnection(
            Context context,
            String fileUrl) throws IOException {
        String currentUrl = fileUrl;

        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            if (!NetworkUtils.isBackendAvailable(context, currentUrl)) {
                throw new IOException(
                        "No usable Internet route or backend DNS resolution failed");
            }

            URL url = new URL(currentUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.setDoInput(true);
            connection.setUseCaches(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.connect();

            int responseCode = connection.getResponseCode();
            if (isRedirectCode(responseCode)) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();

                if (location == null || location.trim().isEmpty()) {
                    throw new IOException("Backend redirect is missing Location header");
                }
                if (redirects >= MAX_REDIRECTS) {
                    throw new IOException("Too many backend redirects");
                }

                String nextUrl = resolveRedirectUrl(currentUrl, location);
                if (isHttpsDowngrade(currentUrl, nextUrl)) {
                    throw new IOException("Refusing HTTPS to HTTP backend redirect");
                }

                // Do not open the redirected URL here. Loop first so every actual
                // request destination passes the mandatory VPN-aware backend preflight.
                currentUrl = nextUrl;
                continue;
            }

            if (responseCode < HttpURLConnection.HTTP_OK
                    || responseCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
                connection.disconnect();
                throw new IOException("Backend returned HTTP " + responseCode);
            }
            return connection;
        }

        throw new IOException("Too many backend redirects");
    }

    static boolean isRedirectCode(int responseCode) {
        return responseCode == HttpURLConnection.HTTP_MOVED_PERM
                || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                || responseCode == HttpURLConnection.HTTP_SEE_OTHER
                || responseCode == 307
                || responseCode == 308;
    }

    static String resolveRedirectUrl(String currentUrl, String location) throws IOException {
        return new URL(new URL(currentUrl), location).toString();
    }

    static boolean isHttpsDowngrade(String currentUrl, String nextUrl) throws IOException {
        URL current = new URL(currentUrl);
        URL next = new URL(nextUrl);
        return "https".equalsIgnoreCase(current.getProtocol())
                && "http".equalsIgnoreCase(next.getProtocol());
    }
}
