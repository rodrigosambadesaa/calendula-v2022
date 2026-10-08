/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.drugdb.cima;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CimaRestClientTest {

    @Test
    public void onlyStatus200AndJsonMediaTypeAreAccepted() {
        assertTrue(CimaRestClient.isExpectedJsonResponse(200, "application/json"));
        assertTrue(CimaRestClient.isExpectedJsonResponse(
                200, "Application/JSON; charset=utf-8"));
        assertFalse(CimaRestClient.isExpectedJsonResponse(200, "text/html"));
        assertFalse(CimaRestClient.isExpectedJsonResponse(200, null));
        assertFalse(CimaRestClient.isExpectedJsonResponse(204, "application/json"));
        assertFalse(CimaRestClient.isExpectedJsonResponse(301, "application/json"));
        assertFalse(CimaRestClient.isExpectedJsonResponse(302, "application/json"));
        assertFalse(CimaRestClient.isExpectedJsonResponse(307, "application/json"));
        assertFalse(CimaRestClient.isExpectedJsonResponse(308, "application/json"));
    }

    @Test
    public void acceptsBoundedUtf8IncludingAccents() throws IOException {
        String sample = "{\"nombre\":\"Ácido ascórbico\"}";
        byte[] bytes = sample.getBytes(StandardCharsets.UTF_8);
        assertEquals(sample, CimaRestClient.readBoundedUtf8(
                new ByteArrayInputStream(bytes), bytes.length));
    }

    @Test
    public void rejectsMalformedUtf8InsteadOfReplacingMedicineNameCharacters() throws IOException {
        // 0xC3 must be followed by a UTF-8 continuation byte, not '('.
        byte[] malformed = new byte[] {(byte) 0xc3, (byte) 0x28};
        try {
            CimaRestClient.readBoundedUtf8(
                    new ByteArrayInputStream(malformed), CimaRestClient.MAX_RESPONSE_BYTES);
            fail("Malformed UTF-8 must not become replacement characters");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("UTF-8"));
        }
    }

    @Test
    public void rejectsIncompleteUtf8AtEndOfStream() throws IOException {
        byte[] truncated = new byte[] {(byte) 0xe2, (byte) 0x82};
        try {
            CimaRestClient.readBoundedUtf8(
                    new ByteArrayInputStream(truncated), CimaRestClient.MAX_RESPONSE_BYTES);
            fail("An incomplete multibyte codepoint must be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("UTF-8"));
        }
    }

    @Test
    public void rejectsResponseBeyondLimitEvenWithoutContentLength() throws IOException {
        byte[] bytes = new byte[17];
        Arrays.fill(bytes, (byte) 'X');
        try {
            CimaRestClient.readBoundedUtf8(new ByteArrayInputStream(bytes), 16);
            fail("A response exceeding the byte cap must fail");
        } catch (IOException expected) {
            // A forged or missing Content-Length cannot bypass the streamed limit.
        }
    }

    @Test
    public void zeroLengthResponseIsAllowedOnlyWithinZeroByteLimit() throws IOException {
        assertEquals("", CimaRestClient.readBoundedUtf8(
                new ByteArrayInputStream(new byte[0]), 0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeResponseLimit() throws IOException {
        CimaRestClient.readBoundedUtf8(new ByteArrayInputStream(new byte[0]), -1);
    }
}
