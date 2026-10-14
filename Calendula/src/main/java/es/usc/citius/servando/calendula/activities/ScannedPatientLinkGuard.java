/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

/** Pure, Android-independent guard for untrusted patient-link QR contents. */
final class ScannedPatientLinkGuard {
    static final int MAX_ENCODED_CHARS = 32768;
    static final int MAX_DECODED_BYTES = 24576;
    static final int MAX_UNCOMPRESSED_CHARS = 65536;

    private ScannedPatientLinkGuard() { }

    static boolean isValidRoute(String requestedPackage, String requestedClass,
                                String appPackage) {
        return appPackage != null && !appPackage.isEmpty()
                && appPackage.equals(requestedPackage)
                && (appPackage + ".activities.PatientDetailActivity").equals(requestedClass);
    }

    static boolean isValidPatientId(Long id) {
        return id != null && id > 0L;
    }

    static boolean canDecode(String original) {
        return original != null && !original.isEmpty()
                && original.length() <= MAX_ENCODED_CHARS;
    }

    /**
     * Preserve legacy non-gzip (Base64) QR handling; compressed input is
     * decoded with a strict output limit to prevent QR-triggered zip bombs.
     *
     * @throws IOException on empty/oversized input, malformed gzip or output overflow.
     */
    static String decodePayload(String original, byte[] decoded) throws IOException {
        if (!canDecode(original) || decoded == null
                || decoded.length == 0 || decoded.length > MAX_DECODED_BYTES) {
            throw new IOException("Invalid patient link QR size");
        }
        if (decoded.length < 2 || (decoded[0] & 0xff) != 0x1f
                || (decoded[1] & 0xff) != 0x8b) {
            return original;
        }
        try (GZIPInputStream zipped = new GZIPInputStream(
                new ByteArrayInputStream(decoded));
             Reader reader = new InputStreamReader(zipped, StandardCharsets.UTF_8)) {
            StringBuilder result = new StringBuilder();
            char[] buf = new char[2048];
            int n;
            while ((n = reader.read(buf)) != -1) {
                if (result.length() + n > MAX_UNCOMPRESSED_CHARS) {
                    throw new IOException("Uncompressed patient link QR exceeds limit");
                }
                result.append(buf, 0, n);
            }
            if (result.length() == 0) {
                throw new IOException("Empty patient link QR");
            }
            return result.toString();
        }
    }
}
