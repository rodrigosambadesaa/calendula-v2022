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

    /**
     * Stage an entire archive before installing its entries. Previous implementations
     * replaced existing files as they streamed ZIP entries, so a corrupt or hostile
     * *later* entry could leave a partially installed prescription database.
     */
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
                ? rootPath : rootPath + File.separator;

        // Keep temporary files on the same volume as their final destination.
        File stage = File.createTempFile(".calendula-zip-stage-", ".tmp", path);
        if (!stage.delete() || !stage.mkdir()) {
            throw new IOException("Unable to create ZIP staging directory");
        }

        final String stagePath = stage.getCanonicalPath();
        final String stagePrefix = stagePath + File.separator;
        final java.util.Set<String> uniqueTargets = new java.util.HashSet<>();
        final java.util.List<File> files = new java.util.ArrayList<>();
        final java.util.List<File> destinations = new java.util.ArrayList<>();
        final java.util.List<File> directories = new java.util.ArrayList<>();
        long extractedBytes = 0L;
        int entries = 0;

        try {
            try (ZipInputStream zip = new ZipInputStream(
                    new BufferedInputStream(new FileInputStream(archive)))) {
                ZipEntry entry;
                byte[] buffer = new byte[8192];

                while ((entry = zip.getNextEntry()) != null) {
                    if (++entries > maxEntries) {
                        throw new IOException("Too many ZIP archive entries");
                    }

                    File destination = new File(path, entry.getName()).getCanonicalFile();
                    String destinationPath = destination.getPath();
                    // Reject root itself, traversal, absolute paths, and sibling-prefix tricks.
                    if (!destinationPath.startsWith(rootPrefix)) {
                        throw new IOException("ZIP entry escapes destination directory");
                    }
                    if (!uniqueTargets.add(destinationPath)) {
                        throw new IOException("Duplicate ZIP entry destination");
                    }

                    File staged = new File(stage, entry.getName()).getCanonicalFile();
                    if (!staged.getPath().startsWith(stagePrefix)) {
                        throw new IOException("ZIP entry escapes staging directory");
                    }
                    if (entry.isDirectory()) {
                        if (!staged.isDirectory() && !staged.mkdirs()) {
                            throw new IOException("Could not create staged ZIP directory");
                        }
                        directories.add(destination);
                    } else {
                        File parent = staged.getParentFile();
                        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
                            throw new IOException("Could not create staged ZIP parent directory");
                        }
                        try (FileOutputStream output = new FileOutputStream(staged)) {
                            int read;
                            while ((read = zip.read(buffer)) != -1) {
                                if (read > maxBytes - extractedBytes) {
                                    throw new IOException("ZIP exceeds uncompressed byte limit");
                                }
                                output.write(buffer, 0, read);
                                extractedBytes += read;
                            }
                        }
                        files.add(staged);
                        destinations.add(destination);
                    }
                    zip.closeEntry();
                }
            }

            // Validate all requested filesystem types before moving any staged file.
            for (File directory : directories) {
                if (directory.exists() && !directory.isDirectory()) {
                    throw new IOException("ZIP directory conflicts with an existing file");
                }
            }
            for (File destination : destinations) {
                if (destination.exists() && !destination.isFile()) {
                    throw new IOException("ZIP file conflicts with an existing directory");
                }
                File parent = destination.getParentFile();
                while (parent != null && !parent.getPath().equals(rootPath)) {
                    if (parent.exists() && !parent.isDirectory()) {
                        throw new IOException("ZIP entry parent conflicts with an existing file");
                    }
                    parent = parent.getParentFile();
                }
            }

            // No destination file is touched until ZIP CRC, entry-count and byte limits
            // have all succeeded. Each same-volume rename is atomic for that file.
            for (File directory : directories) {
                if (!directory.isDirectory() && !directory.mkdirs()) {
                    throw new IOException("Could not create ZIP destination directory");
                }
            }
            for (int i = 0; i < files.size(); i++) {
                File destination = destinations.get(i);
                File parent = destination.getParentFile();
                if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
                    throw new IOException("Could not create ZIP destination parent");
                }
                if (!files.get(i).renameTo(destination)) {
                    throw new IOException("Unable to commit staged ZIP entry");
                }
            }
        } finally {
            deleteStagingTree(stage);
        }
    }

    private static void deleteStagingTree(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteStagingTree(child);
                }
            }
        }
        // Cleanup is best effort: preserve the original ZIP/commit failure.
        if (file.exists() && !file.delete()) {
            // The caller may clean up abandoned staging files later.
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
