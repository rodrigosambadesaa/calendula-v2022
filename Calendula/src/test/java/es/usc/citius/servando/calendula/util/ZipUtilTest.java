/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ZipUtilTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void unzipExtractsNestedDirectoryEntries() throws Exception {
        File archive = temp.newFile("nested.zip");
        File destination = temp.newFolder("destination");

        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("nested/"));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("nested/file.txt"));
            zip.write("safe".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        ZipUtil.unzip(archive, destination);

        File extracted = new File(destination, "nested/file.txt");
        assertTrue(extracted.isFile());
        assertEquals("safe", readUtf8(extracted));
    }

    private static String readUtf8(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[256];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    @Test
    public void unzipRejectsSiblingPrefixTraversal() throws Exception {
        File archive = temp.newFile("traversal.zip");
        File destination = temp.newFolder("db");
        File sibling = new File(
                destination.getParentFile(),
                destination.getName() + "-evil");
        assertTrue(sibling.mkdirs());

        String traversalEntry = "../" + sibling.getName() + "/pwned.txt";
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry(traversalEntry));
            zip.write("owned".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        try {
            ZipUtil.unzip(archive, destination);
            fail("Expected path traversal to be rejected");
        } catch (IOException expected) {
            // expected
        }

        assertFalse(new File(sibling, "pwned.txt").exists());
    }

    @Test
    public void unzipRejectsParentTraversal() throws Exception {
        File archive = temp.newFile("parent-traversal.zip");
        File destination = temp.newFolder("root");

        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("../outside.txt"));
            zip.write("owned".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        try {
            ZipUtil.unzip(archive, destination);
            fail("Expected path traversal to be rejected");
        } catch (IOException expected) {
            // expected
        }

        assertFalse(new File(destination.getParentFile(), "outside.txt").exists());
    }
}
