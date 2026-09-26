/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util;

import android.content.Context;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * OkHttp interceptor that enforces Calendula's mandatory network/backend preflight
 * immediately before an application-owned HTTP(S) request is allowed to proceed.
 */
public final class NetworkPreflightInterceptor implements Interceptor {

    private final Context applicationContext;

    public NetworkPreflightInterceptor(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context == null");
        }
        Context app = context.getApplicationContext();
        this.applicationContext = app != null ? app : context;
    }

    /**
     * Installs the guard as a network interceptor, not an application interceptor.
     *
     * OkHttp invokes application interceptors once for a logical call, while redirects and
     * retries can perform additional network exchanges underneath that call. A network
     * interceptor runs for each exchange, which is required by Calendula's rule that every
     * real HTTP(S) request must pass connectivity/backend preflight immediately before it.
     */
    public static OkHttpClient.Builder installOn(OkHttpClient.Builder builder, Context context) {
        if (builder == null) {
            throw new IllegalArgumentException("builder == null");
        }
        builder.addNetworkInterceptor(new NetworkPreflightInterceptor(context));
        return builder;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        String targetUrl = request.url().toString();

        if (!NetworkUtils.isBackendAvailable(applicationContext, targetUrl)) {
            throw new IOException(
                    "Mandatory connectivity preflight failed for " + request.url().host());
        }

        return chain.proceed(request);
    }
}
