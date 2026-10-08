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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZipUtil {

    // The prescription database is downloaded from a legacy, unauthenticated HTTP host.
    // Limit extraction damage even when the archive is corrupt or attacker-controlled.
    private static final long MAX_UNCOMPRESSED_BYTES = 512L * 1024 * 1024;
    private static final int MAX_ENTRIES = 1024;

    public static void unzip(File archive, File path) throws IOException {
        unzip(archive, path, MAX_UNCOMPRESSED_BYTES, MAX_ENTRIES);
    }

    /** Package-visible to exercise small limits without allocating huge test archives. */
    static void unzip(File archive, File path, long maxBytes, int maxEntries)
            throws IOException {
        if (maxBytes < 0 || maxEntries < 0) {
            throw new IllegalArgumentException("Extraction limits must be nonnegative");
        }
        if (!path.exists() && !path.mkdirs()) {
            throw new IOException("Could not create ZIP destination directory");
        }

        final String rootPath = path.getCanonicalPath();
        final String rootPrefix = rootPath.endsWith(File.separator)
                ? rootPath
                : rootPath + File.separator;
        long extractedBytes = 0L;
        int entries = 0;

        try (ZipInputStream zip = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(archive)))) {
            ZipEntry zipEntry;
            byte[] buffer = new byte[8192];

            while ((zipEntry = zip.getNextEntry()) != null) {
                if (++entries > maxEntries) {
                    throw new IOException("Too many ZIP archive entries");
                }

                final File outputFile = new File(path, zipEntry.getName());
                final String outputPath = outputFile.getCanonicalPath();
                // An entry must be under the destination, not a sibling sharing its prefix.
                if (!outputPath.equals(rootPath) && !outputPath.startsWith(rootPrefix)) {
                    throw new IOException("ZIP entry escapes destination directory");
                }

                if (zipEntry.isDirectory()) {
                    if (!outputFile.exists() && !outputFile.mkdirs()) {
                        throw new IOException("Could not create ZIP directory");
                    }
                } else {
                    File parent = outputFile.getParentFile();
                    if (parent == null) {
                        throw new IOException("ZIP entry has no parent");
                    }
                    if (!parent.exists() && !parent.mkdirs()) {
                        throw new IOException("Could not create ZIP parent directory");
                    }

                    // Complete an entry in a temporary file, preserving any existing
                    // destination if the archive fails its integrity or size checks.
                    File temporary = File.createTempFile(".calendula-unzip-", ".tmp", parent);
                    try {
                        try (FileOutputStream output = new FileOutputStream(temporary)) {
                            int read;
                            while ((read = zip.read(buffer)) != -1) {
                                if (read > maxBytes - extractedBytes) {
                                    throw new IOException("ZIP exceeds uncompressed byte limit");
                                }
                                output.write(buffer, 0, read);
                                extractedBytes += read;
                            }
                        }
                        if (!temporary.renameTo(outputFile)) {
                            throw new IOException("Unable to commit extracted ZIP entry");
                        }
                    } finally {
                        if (temporary.exists() && !temporary.delete()) {
                            // The original exception (if any) is more informative.
                            // Cleanup of a partially written extraction is best effort.
                        }
                    }
                }

                zip.closeEntry();
            }
        }
    }

    public static void writeToStream(InputStream in, OutputStream out, boolean closeOnExit) throws IOException {
        byte[] bytes = new byte[2048];
        for (int c = in.read(bytes); c != -1; c = in.read(bytes)) {
            out.write(bytes, 0, c);
        }
        if (closeOnExit) {
            in.close();
            out.close();
        }
    }

    public static String writeToString(InputStream stream) throws java.io.IOException {
        StringBuffer fileData = new StringBuffer(1000);
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "utf-8"));
        char[] buf = new char[1024];
        int numRead = 0;
        while ((numRead = reader.read(buf)) != -1) {
            String readData = String.valueOf(buf, 0, numRead);
            fileData.append(readData);
            buf = new char[1024];
        }
        reader.close();
        return fileData.toString();
    }
}
