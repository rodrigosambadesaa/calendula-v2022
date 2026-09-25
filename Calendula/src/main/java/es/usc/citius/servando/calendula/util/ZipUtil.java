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

    private static final String TAG = "ZipUtil";

    public static void unzip(File archive, File path) throws IOException {
        if (!path.exists() && !path.mkdirs()) {
            throw new IOException("Could not create ZIP destination directory");
        }

        final String rootPath = path.getCanonicalPath();
        final String rootPrefix = rootPath.endsWith(File.separator)
                ? rootPath
                : rootPath + File.separator;

        try (ZipInputStream zip = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(archive)))) {
            ZipEntry zipEntry;
            while ((zipEntry = zip.getNextEntry()) != null) {
                final File outputFile = new File(path, zipEntry.getName());
                final String outputPath = outputFile.getCanonicalPath();

                // A plain startsWith(rootPath) check is insufficient: a sibling such
                // as "/cache/db-evil" also starts with "/cache/db". Require the
                // canonical destination itself or a child path separated by the
                // platform path separator.
                if (!outputPath.equals(rootPath) && !outputPath.startsWith(rootPrefix)) {
                    throw new IOException("ZIP entry escapes destination directory");
                }

                if (zipEntry.isDirectory()) {
                    if (!outputFile.exists() && !outputFile.mkdirs()) {
                        throw new IOException(
                                "Could not create ZIP directory: " + zipEntry.getName());
                    }
                } else {
                    File parent = outputFile.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        throw new IOException(
                                "Could not create ZIP parent directory: " + zipEntry.getName());
                    }
                    try (FileOutputStream output = new FileOutputStream(outputFile)) {
                        writeToStream(zip, output, false);
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