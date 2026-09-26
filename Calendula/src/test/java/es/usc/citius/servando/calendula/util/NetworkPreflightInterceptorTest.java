/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import okhttp3.OkHttpClient;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class NetworkPreflightInterceptorTest {

    @Test
    public void installOnUsesNetworkInterceptorLayer() {
        Context context = ApplicationProvider.getApplicationContext();
        OkHttpClient client = NetworkPreflightInterceptor.installOn(
                new OkHttpClient.Builder(), context).build();

        assertEquals(0, client.interceptors().size());
        assertEquals(1, client.networkInterceptors().size());
        assertTrue(client.networkInterceptors().get(0) instanceof NetworkPreflightInterceptor);
    }
}
