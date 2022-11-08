package es.usc.citius.servando.calendula.util.debug;

import android.content.Context;

import okhttp3.OkHttpClient;

public class StethoHelper implements StethoHelperInterface {
    @Override
    public void init(Context context) {
        // noop
    }

    @Override
    public void configureInterceptor(OkHttpClient.Builder clientBuilder) {
        // noop
    }
}
