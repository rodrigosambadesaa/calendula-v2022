/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.login;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import net.openid.appauth.connectivity.ConnectionBuilder;

import java.io.IOException;
import java.net.HttpURLConnection;

import es.usc.citius.servando.calendula.util.NetworkUtils;

/**
 * AppAuth connection builder decorator that runs the same mandatory VPN-aware
 * Internet/backend preflight used by the rest of Calendula before opening the
 * underlying token or authorization-service HTTP(S) connection.
 */
public final class PreflightConnectionBuilder implements ConnectionBuilder {

    private final Context applicationContext;
    private final ConnectionBuilder delegate;

    public PreflightConnectionBuilder(Context context, ConnectionBuilder delegate) {
        if (context == null) {
            throw new IllegalArgumentException("context == null");
        }
        if (delegate == null) {
            throw new IllegalArgumentException("delegate == null");
        }
        Context app = context.getApplicationContext();
        this.applicationContext = app != null ? app : context;
        this.delegate = delegate;
    }

    @NonNull
    @Override
    public HttpURLConnection openConnection(@NonNull Uri uri) throws IOException {
        if (!NetworkUtils.isBackendAvailable(applicationContext, uri.toString())) {
            throw new IOException("Mandatory connectivity preflight failed for " + uri.getHost());
        }
        return delegate.openConnection(uri);
    }
}
