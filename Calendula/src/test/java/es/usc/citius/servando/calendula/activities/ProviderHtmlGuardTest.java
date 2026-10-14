/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import org.junit.Test;
import es.usc.citius.servando.calendula.util.prospects.LeafletHtmlPostProcessor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ProviderHtmlGuardTest {
    @Test
    public void nullEmptyAndOversizedDocumentsAreNotProcessed() {
        assertFalse(ProviderHtmlGuard.isBoundedHtml(null));
        assertFalse(ProviderHtmlGuard.isBoundedHtml(""));
        assertTrue(ProviderHtmlGuard.isBoundedHtml("<p>Valid</p>"));
        assertFalse(ProviderHtmlGuard.isBoundedHtml(
                new String(new char[ProviderHtmlGuard.MAX_HTML_CHARS + 1])));
        assertNull(ProviderHtmlGuard.processOrNull(null, html -> html));
        assertNull(ProviderHtmlGuard.processOrNull("", html -> html));
    }

    @Test
    public void sanitizerFailureMustNotFallBackToUnsanitizedHtml() {
        String malicious = "<script>untrusted()</script>";
        assertNull(ProviderHtmlGuard.processOrNull(malicious, html -> {
            throw new IllegalArgumentException("Synthetic sanitizer failure");
        }));
        assertNull(ProviderHtmlGuard.processOrNull(malicious, html -> null));
        assertNull(ProviderHtmlGuard.processOrNull(malicious, html -> ""));
        assertNull(ProviderHtmlGuard.processOrNull(malicious, null));
        assertEquals("<p>Clean</p>",
                ProviderHtmlGuard.processOrNull(malicious, html -> "<p>Clean</p>"));
    }

    @Test
    public void actualMedicationLeafletSanitizerDropsExecutableHtml() {
        String source = "<div><script>stealPatientData()</script>"
                + "<p onclick=\"execute()\">Medication instructions</p>"
                + "<a href=\"javascript:alert(1)\">Unsafe URL</a></div>";
        String cleaned = ProviderHtmlGuard.processOrNull(source,
                new LeafletHtmlPostProcessor()::process);
        org.junit.Assert.assertNotNull(cleaned);
        assertTrue(cleaned.contains("Medication instructions"));
        assertFalse(cleaned.contains("<script"));
        assertFalse(cleaned.contains("onclick"));
        assertFalse(cleaned.contains("javascript:"));
    }

    @Test
    public void sanitizerOutputGrowthBeyondMemoryBudgetIsRejected() {
        String oversized = new String(new char[ProviderHtmlGuard.MAX_HTML_CHARS + 1])
                .replace((char) 0, 'x');
        assertNull(ProviderHtmlGuard.processOrNull("<p>Source</p>",
                html -> oversized));
        assertEquals("safe",
                ProviderHtmlGuard.processOrNull("input", html -> "safe"));
    }
}
