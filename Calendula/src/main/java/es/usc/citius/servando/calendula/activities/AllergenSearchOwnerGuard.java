/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Identity policy shared by asynchronous allergy search and edit screens. */
final class AllergenSearchOwnerGuard {
    private AllergenSearchOwnerGuard() { }

    /**
     * Save only the digest, not an authenticated patient code, into the
     * activity's restoration bundle. Null/blank imported owners fail closed.
     */
    static String fingerprint(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(code.getBytes(StandardCharsets.UTF_8));
            char[] digits = "0123456789abcdef".toCharArray();
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                int unsigned = b & 0xff;
                hex.append(digits[unsigned >>> 4]);
                hex.append(digits[unsigned & 0xf]);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException missingProvider) {
            throw new IllegalStateException("SHA-256 unavailable", missingProvider);
        }
    }

    static boolean samePatient(Long openedId, String openedFingerprint,
                               Long activeId, String activeCode) {
        return openedId != null && openedId > 0L && openedId.equals(activeId)
                && openedFingerprint != null
                && openedFingerprint.equals(fingerprint(activeCode));
    }

    static boolean canStore(Long openedId, String openedFingerprint,
                            Long activeId, String activeCode,
                            Long recordId, String recordCode) {
        return samePatient(openedId, openedFingerprint, activeId, activeCode)
                && samePatient(openedId, openedFingerprint, recordId, recordCode);
    }
}
