/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class CalendulaAppLegacyExportTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @SuppressWarnings("deprecation")
    @Test
    public void unsupportedRawDatabaseExportCannotTruncateExistingBackup()
            throws IOException {
        File existingBackup = temp.newFile("existing-backup.db");
        byte[] trusted = "previous complete synthetic backup"
                .getBytes(StandardCharsets.UTF_8);
        Files.write(existingBackup.toPath(), trusted);

        try {
            new CalendulaApp().exportDatabase(null, "uncheckpointed.db", existingBackup);
            fail("An unsafe raw SQLite export must be rejected explicitly");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage().contains("unsafe"));
        }
        assertArrayEquals(trusted, Files.readAllBytes(existingBackup.toPath()));
    }

    @SuppressWarnings("deprecation")
    @Test
    public void unsupportedRawDatabaseExportCannotPublishNewPartialFile()
            throws IOException {
        File newBackup = new File(temp.getRoot(), "not-created.db");
        try {
            new CalendulaApp().exportDatabase(null, "uncheckpointed.db", newBackup);
            fail("An unsafe raw SQLite export must be rejected explicitly");
        } catch (UnsupportedOperationException expected) {
            assertTrue(expected.getMessage().contains("disabled"));
        }
        assertFalse(newBackup.exists());
    }
}
