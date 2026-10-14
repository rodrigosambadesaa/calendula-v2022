/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import org.junit.Test;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.*;

/** Pure unit tests: no token or patient data leaves the test process. */
public class ScannedPatientLinkGuardTest {
    private static final String APP = "es.usc.citius.servando.calendula";

    @Test
    public void cannotRedirectPatientQRToExternalOrUnrelatedActivities() {
        String details = APP + ".activities.PatientDetailActivity";
        assertTrue(ScannedPatientLinkGuard.isValidRoute(APP, details, APP, details));
        assertTrue(ScannedPatientLinkGuard.isValidRoute(
                APP + ".dev", details, APP + ".dev", details));
        assertTrue(ScannedPatientLinkGuard.isValidRoute(
                APP + ".alpha", details, APP + ".alpha", details));
        assertFalse(ScannedPatientLinkGuard.isValidRoute(
                "com.other.app", details, APP, details));
        assertFalse(ScannedPatientLinkGuard.isValidRoute(
                APP, APP + ".activities.MedicinesActivity", APP, details));
        assertFalse(ScannedPatientLinkGuard.isValidRoute(
                APP, null, APP, details));
        assertFalse(ScannedPatientLinkGuard.isValidRoute(
                APP, details, APP, null));
        assertFalse(ScannedPatientLinkGuard.isValidPatientId(-1L));
        assertFalse(ScannedPatientLinkGuard.isValidPatientId(0L));
        assertFalse(ScannedPatientLinkGuard.isValidPatientId(null));
        assertTrue(ScannedPatientLinkGuard.isValidPatientId(12L));
    }

    @Test
    public void validCompressedPatientTokenDecodesUsingUtf8() throws Exception {
        String json = "{\"token\":\"enlace-ñ-á-é\"}";
        byte[] gzip = gzip(json);
        assertEquals(json, ScannedPatientLinkGuard.decodePayload("encoded", gzip));
    }

    @Test
    public void legacyNonGzipBase64IsPreserved() throws Exception {
        assertEquals("eyJ0b2tlbiI6InQifQ==",
                ScannedPatientLinkGuard.decodePayload(
                        "eyJ0b2tlbiI6InQifQ==", "{}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void emptyTruncatedAndOversizedPayloadsAreRejected() throws Exception {
        assertFalse(ScannedPatientLinkGuard.canDecode(null));
        assertFalse(ScannedPatientLinkGuard.canDecode(""));
        assertFalse(ScannedPatientLinkGuard.canDecode(
                repeat('a', ScannedPatientLinkGuard.MAX_ENCODED_CHARS + 1)));
        expectInvalid("encoded", new byte[0]);
        expectInvalid("encoded", new byte[ScannedPatientLinkGuard.MAX_DECODED_BYTES + 1]);
        expectInvalid("encoded", new byte[]{0x1f, (byte) 0x8b, 0x00});
    }

    @Test
    public void compressedZipBombIsStoppedAtConfiguredLimit() throws Exception {
        String big = repeat('z', ScannedPatientLinkGuard.MAX_UNCOMPRESSED_CHARS + 1);
        expectInvalid("encoded", gzip(big));
    }

    private static byte[] gzip(String value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(bytes)) {
            out.write(value.getBytes(StandardCharsets.UTF_8));
        }
        return bytes.toByteArray();
    }

    private static void expectInvalid(String input, byte[] decoded) {
        try {
            ScannedPatientLinkGuard.decodePayload(input, decoded);
            fail("Invalid patient QR unexpectedly accepted");
        } catch (IOException expected) {
            // Rejected before redirecting or persisting a token.
        }
    }

    private static String repeat(char c, int n) {
        char[] chars = new char[n];
        Arrays.fill(chars, c);
        return new String(chars);
    }
}
