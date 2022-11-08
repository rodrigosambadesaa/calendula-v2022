/*
 *    Calendula - An assistant for personal medication management.
 *    Copyright (C) 2017 CITIUS - USC
 *
 *    Calendula is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this software.  If not, see <http://www.gnu.org/licenses>.
 */

package es.usc.citius.servando.calendula.login;

import android.net.Uri;
import androidx.annotation.NonNull;
import android.text.TextUtils;

import net.openid.appauth.AuthorizationServiceConfiguration;

import es.usc.citius.servando.calendula.BuildConfig;

/**
 * Holds the configuration for OpenID authentication
 */
public class OpenIdProviderConfiguration {

    private static final String TAG = "OpenIdProviderConfig";


    private static OpenIdProviderConfiguration instance;

    private final AuthorizationServiceConfiguration configuration;

    private OpenIdProviderConfiguration() {
        configuration = new AuthorizationServiceConfiguration(getAuthUri(), getTokenUri(), null);
    }

    public static OpenIdProviderConfiguration instance() {
        if (instance == null) {
            instance = new OpenIdProviderConfiguration();
        }
        return instance;
    }

    @NonNull
    public Uri getAuthUri() {
        return Uri.parse(BuildConfig.OAUTH_AUTH_ENDPOINT);
    }

    @NonNull
    public Uri getTokenUri() {
        return Uri.parse(BuildConfig.OAUTH_TOKEN_ENDPOINT);
    }

    @NonNull
    public boolean requiresLogout() {
        return !TextUtils.isEmpty(BuildConfig.OAUTH_LOGOUT_ENDPOINT);
    }

    @NonNull
    public Uri getLogoutUri() {
        return Uri.parse(BuildConfig.OAUTH_LOGOUT_ENDPOINT);
    }

    public String getLogoutRedirect() {
        return BuildConfig.OAUTH_LOGOUT_REDIRECT_SCHEME + "://" + BuildConfig.OAUTH_LOGOUT_REDIRECT_HOST;
    }

    @NonNull
    public String getClientId() {
        return BuildConfig.OAUTH_CLIENT_ID;
    }

    public String getScope() {
        return BuildConfig.OAUTH_SCOPE;
    }

    @NonNull
    public AuthorizationServiceConfiguration getConfiguration() {
        return configuration;
    }


}