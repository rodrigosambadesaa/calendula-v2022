/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import es.usc.citius.servando.calendula.util.ZipUtil;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class ZipExtractionSmokeTest {

    @Test
    public void invalidLaterZipEntryLeavesPriorDatabaseUntouchedOnDevice() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File sandbox = new File(context.getCacheDir(), "zip-smoke-" + System.nanoTime());
        assertTrue(sandbox.mkdirs());
        try {
            File destination = new File(sandbox, "database");
            assertTrue(destination.mkdirs());
            File existing = new File(destination, "AEMPS.sql");
            write(existing, "trusted existing content");

            File archive = new File(sandbox, "untrusted.zip");
            try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
                zip.putNextEntry(new ZipEntry("AEMPS.sql"));
                zip.write("untrusted replacement".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
                zip.putNextEntry(new ZipEntry("../escape.sql"));
                zip.write("not permitted".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            try {
                ZipUtil.unzip(archive, destination);
                fail("Traversal entry must invalidate the entire archive");
            } catch (IOException expected) {
                // Intentionally reject an archive that fails after a plausible first entry.
            }
            assertEquals("trusted existing content", read(existing));
        } finally {
            deleteTree(sandbox);
        }
    }

    @Test
    public void validatedZipCanReplaceExistingDatabaseOnDevice() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File sandbox = new File(context.getCacheDir(), "zip-valid-smoke-" + System.nanoTime());
        assertTrue(sandbox.mkdirs());
        try {
            File destination = new File(sandbox, "database");
            assertTrue(destination.mkdirs());
            File existing = new File(destination, "AEMPS.sql");
            write(existing, "old");
            File archive = new File(sandbox, "trusted.zip");
            try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
                zip.putNextEntry(new ZipEntry("AEMPS.sql"));
                zip.write("new".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            ZipUtil.unzip(archive, destination);
            assertEquals("new", read(existing));
        } finally {
            deleteTree(sandbox);
        }
    }

    private static void write(File file, String value) throws IOException {
        try (FileOutputStream stream = new FileOutputStream(file)) {
            stream.write(value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String read(File file) throws IOException {
        try (FileInputStream stream = new FileInputStream(file)) {
            byte[] buffer = new byte[128];
            int length = stream.read(buffer);
            return new String(buffer, 0, length, StandardCharsets.UTF_8);
        }
    }

    private static void deleteTree(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteTree(child);
                }
            }
        }
        if (file.exists() && !file.delete()) {
            // App-private disposable test cache; best-effort cleanup.
        }
    }
}
