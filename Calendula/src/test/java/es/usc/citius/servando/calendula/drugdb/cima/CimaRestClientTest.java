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
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

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
    @Test
    public void validOfficialJsonResponseMatchesRequestedMedicine() throws Exception {
        HttpURLConnection response = fakeJsonResponse(
                "{\"nregistro\":\"51347\",\"nombre\":\"Synthetic medicine\"}");
        assertEquals("51347", CimaRestClient.parseResponse(response, "51347")
                .getRegistrationNumber());
    }

    @Test
    public void mismatchedMedicineIdentityRejectsOtherwiseValidJson() throws Exception {
        HttpURLConnection response = fakeJsonResponse(
                "{\"nregistro\":\"51347\",\"nombre\":\"Synthetic medicine\"}");
        try {
            CimaRestClient.parseResponse(response, "99999");
            fail("Must not return a different medicine's metadata");
        } catch (IOException expected) {
            // The client must bind body identity to the original query.
        }
    }

    @Test
    public void redirectNeverReadsOrFollowsAResponseBody() throws Exception {
        HttpURLConnection response = fakeJsonResponse("{}");
        when(response.getResponseCode()).thenReturn(302);
        try {
            CimaRestClient.parseResponse(response, "51347");
            fail("Any CIMA redirect must be rejected");
        } catch (IOException expected) {
            verify(response, never()).getInputStream();
        }
    }

    @Test
    public void htmlResponseCannotBeParsedAsMedicine() throws Exception {
        HttpURLConnection response = fakeJsonResponse(
                "{\"nregistro\":\"51347\",\"nombre\":\"Synthetic medicine\"}");
        when(response.getContentType()).thenReturn("text/html");
        try {
            CimaRestClient.parseResponse(response, "51347");
            fail("HTML is not valid CIMA JSON transport");
        } catch (IOException expected) {
            verify(response, never()).getInputStream();
        }
    }

    @Test
    public void forgedUnknownLengthCannotBypassStreamSizeLimit() throws Exception {
        HttpURLConnection response = mock(HttpURLConnection.class);
        when(response.getResponseCode()).thenReturn(200);
        when(response.getContentType()).thenReturn("application/json");
        when(response.getContentLength()).thenReturn(-1);
        when(response.getInputStream()).thenReturn(
                new ByteArrayInputStream(new byte[CimaRestClient.MAX_RESPONSE_BYTES + 1]));
        try {
            CimaRestClient.parseResponse(response, "51347");
            fail("Oversized unknown-length HTTP response must be rejected");
        } catch (IOException expected) {
            // A forged or absent length header is never trusted.
        }
    }

    @Test
    public void medicineSearchReusesTheStrictBoundedHttpResponseValidator()
            throws Exception {
        HttpURLConnection response = fakeJsonResponse(
                "{\"totalFilas\":1,\"pagina\":1,"
                        + "\"resultados\":[{\"nregistro\":\"51347\","
                        + "\"nombre\":\"ASPIRINA C\"}]}");
        CimaMedicineSearch.Page page = CimaMedicineSearch.parsePage(
                CimaRestClient.readJsonResponse(response), 1);
        assertEquals("51347", page.getResults().get(0).getRegistrationNumber());
    }

    @Test
    public void medicineSearchRedirectIsRejectedWithoutReadingBody()
            throws Exception {
        HttpURLConnection response = fakeJsonResponse("{}");
        when(response.getResponseCode()).thenReturn(302);
        try {
            CimaRestClient.readJsonResponse(response);
            fail("Search may not follow HTTP redirects");
        } catch (IOException expected) {
            verify(response, never()).getInputStream();
        }
    }

    private static HttpURLConnection fakeJsonResponse(String json) throws IOException {
        HttpURLConnection response = mock(HttpURLConnection.class);
        when(response.getResponseCode()).thenReturn(200);
        when(response.getContentType()).thenReturn("application/json; charset=utf-8");
        when(response.getContentLength()).thenReturn(-1);
        when(response.getInputStream()).thenReturn(new ByteArrayInputStream(
                json.getBytes(StandardCharsets.UTF_8)));
        return response;
    }

}
