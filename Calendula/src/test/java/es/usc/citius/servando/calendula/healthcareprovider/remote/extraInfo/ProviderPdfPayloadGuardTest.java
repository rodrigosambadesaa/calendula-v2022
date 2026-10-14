/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.healthcareprovider.remote.extraInfo;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProviderPdfPayloadGuardTest {
    @Test
    public void normalProviderPdfIsAccepted() {
        assertTrue(ProviderPdfPayloadGuard.canAttemptDecode(4096));
        assertTrue(ProviderPdfPayloadGuard.canStoreDecoded(3072));
        assertTrue(ProviderPdfPayloadGuard.canStoreDecoded(
                ProviderPdfPayloadGuard.MAX_DECODED_BYTES));
    }

    @Test
    public void missingOversizedAndOverflowedPayloadsAreRejected() {
        assertFalse(ProviderPdfPayloadGuard.canAttemptDecode(0));
        assertFalse(ProviderPdfPayloadGuard.canAttemptDecode(-1));
        assertFalse(ProviderPdfPayloadGuard.canAttemptDecode(Integer.MAX_VALUE));
        assertFalse(ProviderPdfPayloadGuard.canAttemptDecode(
                ProviderPdfPayloadGuard.MAX_ENCODED_CHARS + 1));
        assertFalse(ProviderPdfPayloadGuard.canStoreDecoded(0));
        assertFalse(ProviderPdfPayloadGuard.canStoreDecoded(-1));
        assertFalse(ProviderPdfPayloadGuard.canStoreDecoded(
                ProviderPdfPayloadGuard.MAX_DECODED_BYTES + 1));
        assertFalse(ProviderPdfPayloadGuard.canStoreDecoded(Integer.MAX_VALUE));
    }

    @Test
    public void cappedBase64AllocationHasBoundedOutputSize() {
        // At most three decoded bytes per four input characters, even
        // when the server omits line breaks or padding.
        long maxDecodedAllocation =
                (ProviderPdfPayloadGuard.MAX_ENCODED_CHARS + 3L) / 4L * 3L;
        assertTrue(maxDecodedAllocation < 36L * 1024L * 1024L);
    }
}
