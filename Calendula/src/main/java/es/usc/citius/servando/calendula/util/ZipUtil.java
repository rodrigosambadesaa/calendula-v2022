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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
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
        boolean preserveStage = false;

        try {
            try (ZipInputStream zip = new ZipInputStream(
                    new BufferedInputStream(new FileInputStream(archive)))) {
                ZipEntry entry;

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
                            extractedBytes += copyEntryLimited(
                                    zip, output, maxBytes - extractedBytes);
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

            // ZIP validation is complete. Keep originals in the staging volume
            // until every destination has been installed, and roll back on I/O
            // failures so a later failed rename cannot leave a partial catalog.
            commitStagedFiles(files, destinations, directories, stage, rootPath);
        } catch (IncompleteZipRollbackException failure) {
            // Recovery failed: never delete the only remaining backup copies.
            // The exception includes the recovery directory for diagnostics.
            preserveStage = true;
            throw failure;
        } finally {
            if (!preserveStage) {
                deleteStagingTree(stage);
            }
        }
    }

    /**
     * Install a validated set of staged files. Package-private to allow a
     * deterministic test that fails the *second* rename without depending on
     * platform filesystem permissions.
     */
    static void commitStagedFiles(java.util.List<File> files,
                                  java.util.List<File> destinations,
                                  java.util.List<File> directories,
                                  File stage, String rootPath) throws IOException {
        if (files.size() != destinations.size()) {
            throw new IllegalArgumentException("ZIP staged files and targets differ");
        }
        // A unique, same-volume directory not controlled by archive entry names.
        File backupDir = File.createTempFile(".calendula-zip-backup-", ".tmp", stage);
        if (!backupDir.delete() || !backupDir.mkdir()) {
            throw new IOException("Unable to prepare ZIP rollback directory");
        }

        File[] backups = new File[files.size()];
        boolean[] installed = new boolean[files.size()];
        java.util.List<File> createdDirectories = new java.util.ArrayList<>();
        try {
            for (File directory : directories) {
                ensureInstallDirectory(directory, rootPath, createdDirectories);
            }
            for (int i = 0; i < files.size(); i++) {
                File destination = destinations.get(i);
                ensureInstallDirectory(destination.getParentFile(), rootPath,
                        createdDirectories);

                if (destination.exists()) {
                    if (!destination.isFile()) {
                        throw new IOException("ZIP destination is not a file");
                    }
                    File backup = new File(backupDir, i + ".bak");
                    if (!destination.renameTo(backup)) {
                        throw new IOException("Unable to back up existing ZIP destination");
                    }
                    backups[i] = backup;
                }
                if (!files.get(i).renameTo(destination)) {
                    throw new IOException("Unable to commit staged ZIP entry");
                }
                installed[i] = true;
            }
        } catch (IOException | RuntimeException failure) {
            IOException recoveryFailure =
                    rollbackStagedFiles(destinations, backups, installed, createdDirectories);
            if (recoveryFailure != null) {
                IncompleteZipRollbackException incomplete = new IncompleteZipRollbackException(
                        "ZIP installation and rollback failed; original files may remain at "
                                + backupDir.getAbsolutePath(), failure);
                incomplete.addSuppressed(recoveryFailure);
                throw incomplete;
            }
            throw failure;
        }
    }

    private static void ensureInstallDirectory(File directory, String rootPath,
                                                java.util.List<File> created)
            throws IOException {
        if (directory == null) {
            throw new IOException("Missing ZIP destination parent directory");
        }
        if (directory.isDirectory()) {
            return;
        }
        if (directory.exists() || directory.getPath().equals(rootPath)) {
            throw new IOException("ZIP destination directory is inaccessible");
        }
        ensureInstallDirectory(directory.getParentFile(), rootPath, created);
        if (directory.mkdir()) {
            created.add(directory);
        } else if (!directory.isDirectory()) {
            throw new IOException("Could not create ZIP destination directory");
        }
    }

    private static IOException rollbackStagedFiles(java.util.List<File> destinations,
                                                    File[] backups, boolean[] installed,
                                                    java.util.List<File> createdDirectories) {
        IOException failure = null;
        // Undo in reverse order to restore all original files, including one
        // moved into backup immediately before a subsequent failed rename.
        for (int i = destinations.size() - 1; i >= 0; i--) {
            File destination = destinations.get(i);
            if (installed[i] && !destination.delete()) {
                failure = addRollbackFailure(failure, "Unable to remove installed ZIP entry");
            }
            if (backups[i] != null && !backups[i].renameTo(destination)) {
                failure = addRollbackFailure(failure, "Unable to restore original ZIP entry");
            }
        }
        for (int i = createdDirectories.size() - 1; i >= 0; i--) {
            if (!createdDirectories.get(i).delete()) {
                failure = addRollbackFailure(failure, "Unable to remove newly created ZIP directory");
            }
        }
        return failure;
    }

    private static IOException addRollbackFailure(IOException previous, String message) {
        IOException next = new IOException(message);
        if (previous != null) {
            next.addSuppressed(previous);
        }
        return next;
    }

    private static final class IncompleteZipRollbackException extends IOException {
        IncompleteZipRollbackException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Copy a single ZIP entry without permitting an unbounded CPU loop if a
     * corrupted/decorated stream repeatedly returns zero instead of progress.
     * Keep the global uncompressed archive byte budget enforced before writes.
     */
    static long copyEntryLimited(InputStream input, OutputStream output, long maxBytes)
            throws IOException {
        if (input == null || output == null || maxBytes < 0) {
            throw new IllegalArgumentException("Invalid ZIP entry stream or byte budget");
        }
        byte[] buffer = new byte[8192];
        int emptyReads = 0;
        long copied = 0L;
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (read == 0) {
                if (++emptyReads > 3) {
                    throw new IOException("ZIP entry stream made no progress");
                }
                continue;
            }
            emptyReads = 0;
            if (read > maxBytes - copied) {
                throw new IOException("ZIP exceeds uncompressed byte limit");
            }
            output.write(buffer, 0, read);
            copied += read;
        }
        return copied;
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

    /**
     * Copy with a bounded tolerance for a broken stream returning zero bytes
     * without EOF. Cleanup must run even when a read or write throws, or the
     * importer can leak file descriptors during failed archive setup.
     */
    public static void writeToStream(InputStream in, OutputStream out, boolean closeOnExit)
            throws IOException {
        if (in == null || out == null) {
            throw new IllegalArgumentException("Both source and destination streams are required");
        }
        try {
            byte[] bytes = new byte[2048];
            int consecutiveEmptyReads = 0;
            int read;
            while ((read = in.read(bytes)) != -1) {
                if (read == 0) {
                    if (++consecutiveEmptyReads > 3) {
                        throw new IOException("Stream copy made no progress");
                    }
                    continue;
                }
                consecutiveEmptyReads = 0;
                out.write(bytes, 0, read);
            }
        } finally {
            if (closeOnExit) {
                try {
                    in.close();
                } finally {
                    out.close();
                }
            }
        }
    }

    // Historical text conversion is public even though current callers use
    // dedicated bounded HTTP helpers. Do not allow arbitrary input to grow a
    // medical document buffer without a maximum.
    static final int MAX_STREAM_TEXT_CHARS = 2_000_000;

    public static String writeToString(InputStream stream) throws IOException {
        return writeToString(stream, MAX_STREAM_TEXT_CHARS);
    }

    static String writeToString(InputStream stream, int maxChars) throws IOException {
        if (stream == null) {
            throw new IllegalArgumentException("Input stream is required");
        }
        if (maxChars < 0 || maxChars > MAX_STREAM_TEXT_CHARS) {
            throw new IllegalArgumentException("Invalid maximum text length");
        }
        StringBuilder fileData = new StringBuilder(Math.min(maxChars, 1024));
        // Preserve the historical ownership contract: this helper closes the
        // input, including when decoding, reading or capacity checking fails.
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            char[] buf = new char[1024];
            int emptyReads = 0;
            int numRead;
            while ((numRead = reader.read(buf)) != -1) {
                if (numRead == 0) {
                    if (++emptyReads > 3) {
                        throw new IOException("Text stream made no progress");
                    }
                    continue;
                }
                emptyReads = 0;
                if (numRead > maxChars - fileData.length()) {
                    throw new IOException("Text stream exceeds the maximum length");
                }
                fileData.append(buf, 0, numRead);
            }
        }
        return fileData.toString();
    }
}
