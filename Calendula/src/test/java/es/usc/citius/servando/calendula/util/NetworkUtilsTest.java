/*
 *    Calendula - An assistant for personal medication management.
 *    Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 *    Calendula is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 3 of the License, or
 *    (at your option) any later version.
 */

package es.usc.citius.servando.calendula.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 27)
public class NetworkUtilsTest {

    @Test
    public void boundedDnsExecutorRejectsExcessLookupsWithoutGrowingThreads() throws Exception {
        ThreadPoolExecutor pool = NetworkUtils.newBoundedDnsExecutor(1, 1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> active = null;
        Future<?> queued = null;
        try {
            active = pool.submit(() -> {
                started.countDown();
                release.await();
                return null;
            });
            assertTrue("First DNS task should start", started.await(5, TimeUnit.SECONDS));
            queued = pool.submit(() -> null);
            assertEquals(1, pool.getActiveCount());
            assertEquals(1, pool.getQueue().size());
            assertEquals(1, pool.getMaximumPoolSize());
            try {
                pool.submit(() -> null);
                org.junit.Assert.fail("Bounded DNS worker pool must reject overflow");
            } catch (RejectedExecutionException expected) {
                // Saturation rejects the backend preflight rather than creating
                // another potentially uninterruptible native DNS worker.
            }
        } finally {
            release.countDown();
            if (active != null) active.get(5, TimeUnit.SECONDS);
            if (queued != null) queued.get(5, TimeUnit.SECONDS);
            pool.shutdownNow();
        }
    }

    @Test
    public void dnsExecutorWorkersAreDaemonThreadsAndInvalidSizesAreRejected() {
        ThreadPoolExecutor pool = NetworkUtils.newBoundedDnsExecutor(2, 4);
        try {
            Thread worker = pool.getThreadFactory().newThread(() -> { });
            assertTrue(worker.isDaemon());
            assertEquals(2, pool.getMaximumPoolSize());
            assertEquals(4, pool.getQueue().remainingCapacity());
            org.junit.Assert.assertThrows(IllegalArgumentException.class,
                    () -> NetworkUtils.newBoundedDnsExecutor(0, 1));
            org.junit.Assert.assertThrows(IllegalArgumentException.class,
                    () -> NetworkUtils.newBoundedDnsExecutor(1, 0));
        } finally {
            pool.shutdownNow();
        }
    }

    private static final String BACKEND_URL =
            "https://www.aemps.gob.es/cima/dochtml/p/example";

    @Test
    public void mandatoryActiveProbeRunsBeforeActualBackendResolution() throws Exception {
        NetworkFixture fixture = connectedWifi();
        NetworkUtils.ActiveInternetProbe activeProbe =
                mock(NetworkUtils.ActiveInternetProbe.class);
        NetworkUtils.BackendHostResolver resolver =
                mock(NetworkUtils.BackendHostResolver.class);

        when(activeProbe.isReachable(fixture.context)).thenReturn(true);
        when(resolver.resolves("www.aemps.gob.es", fixture.network)).thenReturn(true);

        assertTrue(NetworkUtils.isBackendAvailable(
                fixture.context, BACKEND_URL, activeProbe, resolver));

        InOrder order = inOrder(activeProbe, resolver);
        order.verify(activeProbe).isReachable(fixture.context);
        order.verify(resolver).resolves("www.aemps.gob.es", fixture.network);
    }

    @Test
    public void failedActiveProbePreventsBackendResolution() throws Exception {
        NetworkFixture fixture = connectedWifi();
        NetworkUtils.ActiveInternetProbe activeProbe =
                mock(NetworkUtils.ActiveInternetProbe.class);
        NetworkUtils.BackendHostResolver resolver =
                mock(NetworkUtils.BackendHostResolver.class);

        when(activeProbe.isReachable(fixture.context)).thenReturn(false);

        assertFalse(NetworkUtils.isBackendAvailable(
                fixture.context, BACKEND_URL, activeProbe, resolver));

        verify(activeProbe).isReachable(fixture.context);
        verifyNoMoreInteractions(resolver);
    }

    @Test
    public void unexpectedActiveProbeFailureFailsClosedWithoutStartingDns() throws Exception {
        NetworkFixture fixture = connectedWifi();
        NetworkUtils.ActiveInternetProbe activeProbe =
                mock(NetworkUtils.ActiveInternetProbe.class);
        NetworkUtils.BackendHostResolver resolver =
                mock(NetworkUtils.BackendHostResolver.class);

        // Real device connectivity may change between the route snapshot and
        // the active probe, including throwing from a vendor network stack.
        when(activeProbe.isReachable(fixture.context))
                .thenThrow(new IllegalStateException("transient network state"));

        assertFalse(NetworkUtils.isBackendAvailable(
                fixture.context, BACKEND_URL, activeProbe, resolver));

        verify(activeProbe).isReachable(fixture.context);
        verifyNoMoreInteractions(resolver);
    }

    @Test
    public void backendDnsFailurePreventsGateFromOpening() throws Exception {
        NetworkFixture fixture = connectedWifi();
        NetworkUtils.ActiveInternetProbe activeProbe =
                mock(NetworkUtils.ActiveInternetProbe.class);
        NetworkUtils.BackendHostResolver resolver =
                mock(NetworkUtils.BackendHostResolver.class);

        when(activeProbe.isReachable(fixture.context)).thenReturn(true);
        when(resolver.resolves("www.aemps.gob.es", fixture.network)).thenReturn(false);

        assertFalse(NetworkUtils.isBackendAvailable(
                fixture.context, BACKEND_URL, activeProbe, resolver));

        verify(activeProbe).isReachable(fixture.context);
        verify(resolver).resolves("www.aemps.gob.es", fixture.network);
    }

    @Test
    public void disconnectedBetweenActiveProbeAndDnsFailsClosed() throws Exception {
        NetworkFixture fixture = connectedWifi();
        ConnectivityManager manager = (ConnectivityManager)
                fixture.context.getSystemService(Context.CONNECTIVITY_SERVICE);
        // isConnected() observes the usable Wi-Fi route. By the time the
        // mock probe finishes, the route has vanished: never resolve through
        // a process-global fallback or accidentally authorize a backend.
        when(manager.getActiveNetwork()).thenReturn(fixture.network, (Network) null);
        NetworkUtils.ActiveInternetProbe probe =
                mock(NetworkUtils.ActiveInternetProbe.class);
        NetworkUtils.BackendHostResolver resolver =
                mock(NetworkUtils.BackendHostResolver.class);
        when(probe.isReachable(fixture.context)).thenReturn(true);

        assertFalse(NetworkUtils.isBackendAvailable(
                fixture.context, BACKEND_URL, probe, resolver));
        verify(probe).isReachable(fixture.context);
        verifyNoMoreInteractions(resolver);
    }

    @Test
    public void routeApiFailureDuringBackendDnsSelectionFailsClosed() throws Exception {
        NetworkFixture fixture = connectedWifi();
        ConnectivityManager manager = (ConnectivityManager)
                fixture.context.getSystemService(Context.CONNECTIVITY_SERVICE);
        when(manager.getActiveNetwork()).thenReturn(fixture.network)
                .thenThrow(new SecurityException("synthetic network permission change"));
        NetworkUtils.ActiveInternetProbe probe =
                mock(NetworkUtils.ActiveInternetProbe.class);
        NetworkUtils.BackendHostResolver resolver =
                mock(NetworkUtils.BackendHostResolver.class);
        when(probe.isReachable(fixture.context)).thenReturn(true);

        assertFalse(NetworkUtils.isBackendAvailable(
                fixture.context, BACKEND_URL, probe, resolver));
        verify(probe).isReachable(fixture.context);
        verifyNoMoreInteractions(resolver);
    }

    @Test
    public void vpnWithoutUnderlyingNetworkStopsBeforeActiveProbe() throws Exception {
        Context context = mock(Context.class);
        ConnectivityManager manager = mock(ConnectivityManager.class);
        Network vpn = mock(Network.class);
        NetworkCapabilities vpnCapabilities = mock(NetworkCapabilities.class);
        NetworkUtils.ActiveInternetProbe activeProbe =
                mock(NetworkUtils.ActiveInternetProbe.class);
        NetworkUtils.BackendHostResolver resolver =
                mock(NetworkUtils.BackendHostResolver.class);

        when(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(manager);
        when(manager.getActiveNetwork()).thenReturn(vpn);
        when(manager.getAllNetworks()).thenReturn(new Network[]{vpn});
        when(manager.getNetworkCapabilities(vpn)).thenReturn(vpnCapabilities);
        when(vpnCapabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET)).thenReturn(true);
        when(vpnCapabilities.hasTransport(
                NetworkCapabilities.TRANSPORT_VPN)).thenReturn(true);
        when(vpnCapabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_NOT_VPN)).thenReturn(false);

        assertFalse(NetworkUtils.isBackendAvailable(
                context, BACKEND_URL, activeProbe, resolver));

        verify(activeProbe, never()).isReachable(context);
        verifyNoMoreInteractions(resolver);
    }

    @Test
    public void malformedAndNonHttpUrlsNeverStartProbes() throws Exception {
        NetworkFixture fixture = connectedWifi();
        NetworkUtils.ActiveInternetProbe activeProbe =
                mock(NetworkUtils.ActiveInternetProbe.class);
        NetworkUtils.BackendHostResolver resolver =
                mock(NetworkUtils.BackendHostResolver.class);

        assertFalse(NetworkUtils.isBackendAvailable(
                fixture.context, "not a URL", activeProbe, resolver));
        assertFalse(NetworkUtils.isBackendAvailable(
                fixture.context, "file:///tmp/offline.html", activeProbe, resolver));

        verifyNoMoreInteractions(activeProbe, resolver);
    }

    private static NetworkFixture connectedWifi() {
        Context context = mock(Context.class);
        ConnectivityManager manager = mock(ConnectivityManager.class);
        Network wifi = mock(Network.class);
        NetworkCapabilities capabilities = mock(NetworkCapabilities.class);

        when(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(manager);
        when(manager.getActiveNetwork()).thenReturn(wifi);
        when(manager.getAllNetworks()).thenReturn(new Network[]{wifi});
        when(manager.getNetworkCapabilities(wifi)).thenReturn(capabilities);
        when(capabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET)).thenReturn(true);
        when(capabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_NOT_VPN)).thenReturn(true);
        when(capabilities.hasTransport(
                NetworkCapabilities.TRANSPORT_VPN)).thenReturn(false);

        return new NetworkFixture(context, wifi);
    }

    private static final class NetworkFixture {
        final Context context;
        final Network network;

        NetworkFixture(Context context, Network network) {
            this.context = context;
            this.network = network;
        }
    }
}
