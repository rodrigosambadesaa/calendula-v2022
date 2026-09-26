package es.usc.citius.servando.calendula.healthcareprovider.remote.extraInfo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ExtraInfoHelperTest {

    @Test
    public void nullResponseIsRejected() {
        assertNull(ExtraInfoHelper.parseExtraInfo(null));
    }

    @Test
    public void providerErrorIsRejected() {
        ProviderResponse response = new ProviderResponse();
        response.error = new ProviderError();
        response.result = "{\"return\":\"YWJj\"}";

        assertNull(ExtraInfoHelper.parseExtraInfo(response));
    }

    @Test
    public void missingOrMalformedResultIsRejected() {
        ProviderResponse missing = new ProviderResponse();
        ProviderResponse malformed = new ProviderResponse();
        malformed.result = "{";

        assertNull(ExtraInfoHelper.parseExtraInfo(missing));
        assertNull(ExtraInfoHelper.parseExtraInfo(malformed));
    }

    @Test
    public void validPdfPayloadIsParsed() {
        ProviderResponse response = new ProviderResponse();
        response.result = "{\"return\":\"YWJj\"}";

        ExtraInfo info = ExtraInfoHelper.parseExtraInfo(response);

        assertNotNull(info);
        assertEquals("YWJj", info.pdf);
    }
}
