package es.usc.citius.servando.calendula.healthcareprovider.remote.extraInfo;

import org.junit.Test;

import java.lang.annotation.Annotation;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import retrofit2.Converter;
import retrofit2.Retrofit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ProviderResponseConverterFactoryTest {

    @Test
    public void parsesProviderResponseFromBodyStream() throws Exception {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://example.test/")
                .build();
        Converter<ResponseBody, ?> converter = ProviderResponseConverterFactory.create()
                .responseBodyConverter(
                        ProviderResponse.class,
                        new Annotation[0],
                        retrofit);
        assertNotNull(converter);

        ResponseBody body = ResponseBody.create(
                MediaType.parse("application/json"),
                "{\"result\":\"{\\\"pdf\\\":\\\"YWJj\\\"}\"}");

        ProviderResponse response = (ProviderResponse) converter.convert(body);

        assertNotNull(response);
        assertNull(response.error);
        assertEquals("{\"pdf\":\"YWJj\"}", response.result);
    }
}
