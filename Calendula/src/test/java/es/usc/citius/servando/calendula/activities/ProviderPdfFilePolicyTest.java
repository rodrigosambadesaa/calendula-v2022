/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProviderPdfFilePolicyTest {
    @Rule public TemporaryFolder files = new TemporaryFolder();

    @Test
    public void onlyProviderPdfInPrivateCacheIsReadableOrDeletable() throws Exception {
        File cache = files.newFolder("cache");
        File valid = new File(cache, "extrainfo123456789.pdf");
        assertTrue(valid.createNewFile());
        assertTrue(ProviderPdfFilePolicy.isTrustedPdf(cache, valid));
        assertTrue(ProviderPdfFilePolicy.isTrustedPdf(cache,
                new File(cache, "./extrainfo123456789.pdf")));

        File other = files.newFile("extrainfo111.pdf");
        assertFalse(ProviderPdfFilePolicy.isTrustedPdf(cache, other));
        assertFalse(ProviderPdfFilePolicy.isTrustedPdf(cache,
                new File(cache, "../extrainfo111.pdf")));
        assertFalse(ProviderPdfFilePolicy.isTrustedPdf(cache,
                new File(cache, "extrainfo-missing.pdf")));
        assertFalse(ProviderPdfFilePolicy.isTrustedPdf(cache, null));
        assertFalse(ProviderPdfFilePolicy.isTrustedPdf(null, valid));
    }

    @Test
    public void nonProviderCacheItemsMustNotBeParsedAsMedicalDocuments() throws Exception {
        File cache = files.newFolder("provider-cache");
        File otherName = new File(cache, "credentials.pdf");
        File wrongExtension = new File(cache, "extrainfo12345.json");
        File noRandomSuffix = new File(cache, "extrainfo.pdf");
        assertTrue(otherName.createNewFile());
        assertTrue(wrongExtension.createNewFile());
        assertTrue(noRandomSuffix.createNewFile());
        assertFalse(ProviderPdfFilePolicy.isTrustedPdf(cache, otherName));
        assertFalse(ProviderPdfFilePolicy.isTrustedPdf(cache, wrongExtension));
        assertFalse(ProviderPdfFilePolicy.isTrustedPdf(cache, noRandomSuffix));
    }
}
