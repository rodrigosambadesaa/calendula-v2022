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
import java.io.FilterInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayInputStream;
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
    public void zipEntryCopyFailsAfterBoundedStallWithoutManufacturingBytes()
            throws Exception {
        final int[] bulkReads = {0};
        InputStream stalled = new InputStream() {
            @Override
            public int read() {
                throw new AssertionError("ZIP copy must not invent single-byte progress");
            }
            @Override
            public int read(byte[] bytes, int offset, int length) {
                bulkReads[0]++;
                return 0;
            }
        };
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ZipUtil.copyEntryLimited(stalled, out, 64);
            fail("ZIP stream that never progresses must fail closed");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("no progress"));
        }
        assertEquals(4, bulkReads[0]);
        assertEquals(0, out.size());
    }

    @Test
    public void zipEntryCopyPreservesNulBytesAfterTemporaryStalls() throws Exception {
        byte[] expected = new byte[]{'A', 0, 'B'};
        InputStream intermittent = new ByteArrayInputStream(expected) {
            private int stalledReads = 2;
            @Override
            public synchronized int read(byte[] bytes, int off, int len) {
                if (stalledReads-- > 0) {
                    return 0;
                }
                return super.read(bytes, off, len);
            }
        };
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertEquals(3L, ZipUtil.copyEntryLimited(intermittent, out, 3L));
        org.junit.Assert.assertArrayEquals(expected, out.toByteArray());
    }

    @Test
    public void zipEntryCopyRejectsLimitOverflowBeforeWritingExcessBytes()
            throws Exception {
        byte[] entry = new byte[]{1, 2, 3, 4};
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ZipUtil.copyEntryLimited(new ByteArrayInputStream(entry), out, 3L);
            fail("ZIP entry must never bypass global uncompressed budget");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("byte limit"));
        }
        assertEquals(0, out.size());
        assertEquals(0L, ZipUtil.copyEntryLimited(
                new ByteArrayInputStream(new byte[0]),
                new ByteArrayOutputStream(), 0L));
    }

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
    @Test
    public void unzipRejectsExcessiveUncompressedSizeWithoutReplacingExistingFile()
            throws Exception {
        File archive = temp.newFile("oversized.zip");
        File destination = temp.newFolder("oversized-root");
        File existing = new File(destination, "AEMPS.sql");
        try (FileOutputStream output = new FileOutputStream(existing)) {
            output.write("original".getBytes(StandardCharsets.UTF_8));
        }
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("AEMPS.sql"));
            zip.write("replacement exceeds limit".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        try {
            ZipUtil.unzip(archive, destination, 8, 10);
            fail("Expected uncompressed byte limit to reject ZIP");
        } catch (IOException expected) {
            // expected
        }

        assertEquals("original", readUtf8(existing));
    }

    @Test
    public void unzipRejectsArchivesWithTooManyEntries() throws Exception {
        File archive = temp.newFile("too-many-entries.zip");
        File destination = temp.newFolder("entry-root");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("one.txt"));
            zip.write(1);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("two.txt"));
            zip.write(2);
            zip.closeEntry();
        }

        try {
            ZipUtil.unzip(archive, destination, 100, 1);
            fail("Expected archive entry limit to reject ZIP");
        } catch (IOException expected) {
            // expected
        }

        // No entry may be installed until the entire archive passes validation.
        assertFalse(new File(destination, "one.txt").exists());
        assertFalse(new File(destination, "two.txt").exists());
    }


    @Test
    public void laterTraversalDoesNotOverwriteAnExistingDatabaseFile() throws Exception {
        File archive = temp.newFile("traversal-later.zip");
        File destination = temp.newFolder("existing-db");
        File existing = new File(destination, "AEMPS.sql");
        try (FileOutputStream out = new FileOutputStream(existing)) {
            out.write("original data".getBytes(StandardCharsets.UTF_8));
        }

        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("AEMPS.sql"));
            zip.write("unverified replacement".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("../outside.sql"));
            zip.write("invalid path".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        try {
            ZipUtil.unzip(archive, destination);
            fail("Expected traversal rejection");
        } catch (IOException expected) {
            // Extraction staging must keep the old file intact.
        }
        assertEquals("original data", readUtf8(existing));
        assertFalse(new File(destination.getParentFile(), "outside.sql").exists());
    }

    @Test
    public void canonicalPathAliasesAreRejectedWithoutInstallingAnyFile() throws Exception {
        File archive = temp.newFile("aliases.zip");
        File destination = temp.newFolder("aliases-root");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("nested/"));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("nested/../AEMPS.sql"));
            zip.write("first".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("AEMPS.sql"));
            zip.write("second".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        try {
            ZipUtil.unzip(archive, destination);
            fail("Expected canonical duplicate ZIP entries to be rejected");
        } catch (IOException expected) {
            // A malicious second entry must not replace the first.
        }
        assertFalse(new File(destination, "AEMPS.sql").exists());
    }

    @Test
    public void fullyValidatedArchiveReplacesFileAndCleansUpStaging() throws Exception {
        File archive = temp.newFile("valid-replacement.zip");
        File destination = temp.newFolder("valid-root");
        File existing = new File(destination, "AEMPS.sql");
        try (FileOutputStream out = new FileOutputStream(existing)) {
            out.write("old".getBytes(StandardCharsets.UTF_8));
        }
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("AEMPS.sql"));
            zip.write("new".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        ZipUtil.unzip(archive, destination);

        assertEquals("new", readUtf8(existing));
        File[] left = destination.listFiles();
        assertEquals(1, left == null ? 0 : left.length);
    }

    @Test
    public void streamCopyAllowsShortTemporaryStallAndKeepsExactPayload() throws Exception {
        byte[] expected = "synthetic archive bytes".getBytes(StandardCharsets.UTF_8);
        InputStream zeroOnce = new ByteArrayInputStream(expected) {
            boolean initiallyStalled = true;
            @Override
            public synchronized int read(byte[] bytes, int off, int len) {
                if (initiallyStalled) {
                    initiallyStalled = false;
                    return 0;
                }
                return super.read(bytes, off, len);
            }
        };
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ZipUtil.writeToStream(zeroOnce, output, false);
        assertEquals("synthetic archive bytes",
                new String(output.toByteArray(), StandardCharsets.UTF_8));
    }

    @Test
    public void permanentlyStalledStreamFailsAndClosesBothResources() throws Exception {
        final boolean[] closed = new boolean[2];
        InputStream stalled = new InputStream() {
            @Override
            public int read() {
                return 0;
            }
            @Override
            public int read(byte[] bytes, int off, int len) {
                return 0;
            }
            @Override
            public void close() {
                closed[0] = true;
            }
        };
        OutputStream output = new ByteArrayOutputStream() {
            @Override
            public void close() throws IOException {
                super.close();
                closed[1] = true;
            }
        };
        try {
            ZipUtil.writeToStream(stalled, output, true);
            fail("The copy must not spin indefinitely on a broken stream");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("no progress"));
        }
        assertTrue("The input must close after failed copy", closed[0]);
        assertTrue("The output must close after failed copy", closed[1]);
    }

    @Test
    public void failedOutputWriteClosesInputAndOutputWhenRequested() throws Exception {
        final boolean[] closed = new boolean[2];
        InputStream input = new FilterInputStream(
                new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8))) {
            @Override
            public void close() throws IOException {
                closed[0] = true;
                super.close();
            }
        };
        OutputStream output = new OutputStream() {
            @Override
            public void write(int value) throws IOException {
                throw new IOException("synthetic storage failure");
            }
            @Override
            public void write(byte[] bytes, int off, int len) throws IOException {
                throw new IOException("synthetic storage failure");
            }
            @Override
            public void close() {
                closed[1] = true;
            }
        };
        try {
            ZipUtil.writeToStream(input, output, true);
            fail("The injected failed write must propagate");
        } catch (IOException expected) {
            assertEquals("synthetic storage failure", expected.getMessage());
        }
        assertTrue(closed[0]);
        assertTrue(closed[1]);
    }

    @Test
    public void legacyTextHelperAllowsExactLimitAndMultibyteUtf8() throws Exception {
        String content = "áé😀";
        byte[] encoded = content.getBytes(StandardCharsets.UTF_8);
        assertEquals(content, ZipUtil.writeToString(
                new ByteArrayInputStream(encoded), content.length()));
    }

    @Test
    public void oversizedTextStreamIsRejectedAndInputClosedOnFailure() throws Exception {
        final boolean[] closed = new boolean[1];
        InputStream input = new FilterInputStream(
                new ByteArrayInputStream("123456".getBytes(StandardCharsets.UTF_8))) {
            @Override
            public void close() throws IOException {
                closed[0] = true;
                super.close();
            }
        };
        try {
            ZipUtil.writeToString(input, 5);
            fail("Stream contents exceeded the explicit text budget");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("maximum length"));
        }
        assertTrue("The stream should be closed after rejecting oversized text", closed[0]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void textStreamCannotBypassItsGlobalMemoryBudget() throws Exception {
        ZipUtil.writeToString(new ByteArrayInputStream(new byte[0]),
                ZipUtil.MAX_STREAM_TEXT_CHARS + 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void unzipRejectsNegativeExtractionLimits() throws Exception {
        ZipUtil.unzip(temp.newFile("unused.zip"), temp.newFolder("unused"), -1, 1);
    }

}
