package es.usc.citius.servando.calendula.healthcareprovider.remote.extraInfo;

import androidx.annotation.Nullable;

import org.hl7.fhir.dstu3.model.Bundle;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import es.usc.citius.servando.calendula.util.GsonUtil;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Converter;
import retrofit2.Retrofit;


public class ProviderResponseConverterFactory extends Converter.Factory {


    public static ProviderResponseConverterFactory create() {
        return new ProviderResponseConverterFactory();
    }

    @Nullable
    @Override
    public Converter<ResponseBody, ?> responseBodyConverter(Type type, Annotation[] annotations, Retrofit retrofit) {
        return ProviderResponseConverter.instance();
    }

    @Nullable
    @Override
    public Converter<?, RequestBody> requestBodyConverter(Type type, Annotation[] parameterAnnotations, Annotation[] methodAnnotations, Retrofit retrofit) {
        return ProviderRequestConverter.instance();
    }


    private final static class ProviderResponseConverter implements Converter<ResponseBody, ProviderResponse> {
        private static ProviderResponseConverter theInstance;

        public static ProviderResponseConverter instance() {
            if (theInstance == null)
                theInstance = new ProviderResponseConverter();
            return theInstance;
        }

        @Override
        public ProviderResponse convert(ResponseBody value) throws IOException {
            final String theBody = value.string();
//            StringReader reader = new StringReader(theBody);
            ProviderResponse response = GsonUtil.get().fromJson(theBody, ProviderResponse.class);
            return response;

        }
    }

    private final static class ProviderRequestConverter implements Converter<Bundle, RequestBody> {
        private static ProviderRequestConverter theInstance;

        public static ProviderRequestConverter instance() {
            if (theInstance == null)
                theInstance = new ProviderRequestConverter();
            return theInstance;
        }


        @Override
        public RequestBody convert(Bundle value) throws IOException {
            String theBody = "";
            return RequestBody.create(MediaType.parse("application/json"), theBody);
        }
    }


}
