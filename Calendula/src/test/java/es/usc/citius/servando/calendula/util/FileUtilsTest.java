/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FileUtilsTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private static String read(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] data = new byte[256];
            int count;
            while ((count = input.read(data)) != -1) {
                output.write(data, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void write(File file, String content) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    public void validBundledArchiveReplacesCacheOnlyAfterFullCopy() throws Exception {
        File cache = temp.newFolder("cache");
        File original = new File(cache, "AEMPS-2.zip");
        write(original, "previous complete catalog");

        File result = FileUtils.copyAssetToCache(
                new ByteArrayInputStream("new complete catalog".getBytes(StandardCharsets.UTF_8)),
                cache, "AEMPS-2.zip");

        assertEquals(original.getCanonicalFile(), result.getCanonicalFile());
        assertEquals("new complete catalog", read(original));
        assertEquals(1, cache.listFiles().length);
    }

    @Test
    public void brokenInputAfterPartialCopyNeverReplacesExistingArchive() throws Exception {
        File cache = temp.newFolder("cache");
        File original = new File(cache, "US-3.zip");
        write(original, "original valid catalog");
        InputStream broken = new InputStream() {
            int calls;
            @Override
            public int read() throws IOException {
                throw new IOException("synthetic asset read failure");
            }
            @Override
            public int read(byte[] data, int off, int len) throws IOException {
                if (calls++ == 0) {
                    data[off] = (byte) 'x';
                    return 1;
                }
                throw new IOException("synthetic asset read failure");
            }
        };
        try {
            FileUtils.copyAssetToCache(broken, cache, "US-3.zip");
            fail("Incomplete archive must not be installed");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("synthetic asset read failure"));
        }
        assertEquals("original valid catalog", read(original));
        assertEquals("No partial temporary archive should survive", 1, cache.listFiles().length);
    }

    @Test
    public void oneTransientEmptyReadStillCopiesAllBytes() throws Exception {
        File cache = temp.newFolder("cache");
        InputStream transientStall = new ByteArrayInputStream(
                "complete".getBytes(StandardCharsets.UTF_8)) {
            boolean first = true;
            @Override
            public synchronized int read(byte[] data, int offset, int count) {
                if (first) {
                    first = false;
                    return 0;
                }
                return super.read(data, offset, count);
            }
        };
        File result = FileUtils.copyAssetToCache(transientStall, cache, "AEMPS.zip");
        assertEquals("complete", read(result));
    }

    @Test
    public void permanentlyStalledInputFailsWithoutLeavingAFile() throws Exception {
        File cache = temp.newFolder("cache");
        InputStream stalled = new InputStream() {
            @Override
            public int read() {
                return 0;
            }
            @Override
            public int read(byte[] data, int off, int len) {
                return 0;
            }
        };
        try {
            FileUtils.copyAssetToCache(stalled, cache, "AEMPS.zip");
            fail("A stalled asset must fail rather than terminate early or loop forever");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("no progress"));
        }
        assertEquals(0, cache.listFiles().length);
    }

    @Test
    public void invalidNameCannotEscapePrivateCacheOrModifyAnotherFile() throws Exception {
        File cache = temp.newFolder("cache");
        File outside = temp.newFile("sensitive.txt");
        write(outside, "untouched");
        String[] names = {"../sensitive.txt", "/tmp/malicious.zip", ".", "..", "nested/file.zip",
                "nested\\file.zip", ""};
        for (String name : names) {
            try {
                FileUtils.copyAssetToCache(
                        new ByteArrayInputStream("bad".getBytes(StandardCharsets.UTF_8)),
                        cache, name);
                fail("Unsafe cache filename accepted: " + name);
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("Invalid private asset"));
            }
        }
        assertEquals("untouched", read(outside));
        assertEquals(0, cache.listFiles().length);
    }
}
