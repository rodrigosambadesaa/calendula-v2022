/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.healthcareprovider.remote.extraInfo;

/** Conservative bounds for untrusted Base64 provider PDF responses. */
final class ProviderPdfPayloadGuard {
    static final int MAX_DECODED_BYTES = 32 * 1024 * 1024;
    // Four Base64 chars per three bytes, plus room for MIME line breaks.
    // This caps the allocation made by Base64.decode on Android.
    static final int MAX_ENCODED_CHARS =
            ((MAX_DECODED_BYTES + 2) / 3) * 4 + 1024 * 1024;

    private ProviderPdfPayloadGuard() {
    }

    static boolean canAttemptDecode(int encodedCharacters) {
        return encodedCharacters > 0 && encodedCharacters <= MAX_ENCODED_CHARS;
    }

    static boolean canStoreDecoded(int decodedBytes) {
        return decodedBytes > 0 && decodedBytes <= MAX_DECODED_BYTES;
    }
}
