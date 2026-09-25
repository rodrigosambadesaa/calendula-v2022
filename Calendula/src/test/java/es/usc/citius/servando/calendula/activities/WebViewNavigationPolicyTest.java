/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WebViewNavigationPolicyTest {

    private static final String TRUSTED =
            "https://www.aemps.gob.es/cima/dochtml/p/123/Prospecto_123.html";

    @Test
    public void sameOriginNavigationStaysInsideWebView() {
        assertTrue(WebViewNavigationPolicy.shouldOpenInsideWebView(
                TRUSTED,
                "https://www.aemps.gob.es/cima/dochtml/p/456/Prospecto_456.html",
                false));
    }

    @Test
    public void defaultHttpsPortMatchesExplicit443() {
        assertTrue(WebViewNavigationPolicy.isSameOrigin(
                TRUSTED,
                "https://www.aemps.gob.es:443/another/path"));
    }

    @Test
    public void schemeChangeIsCrossOrigin() {
        assertFalse(WebViewNavigationPolicy.isSameOrigin(
                TRUSTED,
                "http://www.aemps.gob.es/cima/dochtml/p/123/Prospecto_123.html"));
    }

    @Test
    public void trustedUrlInQueryDoesNotMakeForeignHostInternal() {
        assertFalse(WebViewNavigationPolicy.shouldOpenInsideWebView(
                TRUSTED,
                "https://evil.example/?next=" + TRUSTED,
                false));
    }

    @Test
    public void trustedHostInUserInfoDoesNotMakeForeignHostInternal() {
        assertFalse(WebViewNavigationPolicy.shouldOpenInsideWebView(
                TRUSTED,
                "https://www.aemps.gob.es@evil.example/path",
                false));
    }

    @Test
    public void lookalikeSubdomainDoesNotMakeForeignHostInternal() {
        assertFalse(WebViewNavigationPolicy.shouldOpenInsideWebView(
                TRUSTED,
                "https://www.aemps.gob.es.evil.example/path",
                false));
    }

    @Test
    public void explicitExternalLinksSettingAllowsCrossOriginHttpNavigation() {
        assertTrue(WebViewNavigationPolicy.shouldOpenInsideWebView(
                TRUSTED,
                "https://www.accessdata.fda.gov/spl/",
                true));
    }

    @Test
    public void externalLinksSettingDoesNotTurnCustomSchemesIntoWebContent() {
        assertFalse(WebViewNavigationPolicy.shouldOpenInsideWebView(
                TRUSTED,
                "mailto:example@example.com",
                true));
    }

    @Test
    public void localJavascriptAndDataUrlsRemainWebViewLocal() {
        assertTrue(WebViewNavigationPolicy.isWebViewLocalUrl("javascript:alert(1)"));
        assertTrue(WebViewNavigationPolicy.isWebViewLocalUrl("DATA:text/plain,hello"));
        assertTrue(WebViewNavigationPolicy.isWebViewLocalUrl("about:blank"));
    }

    @Test
    public void malformedUrlsAreNeverRemoteHttpUrls() {
        assertFalse(WebViewNavigationPolicy.isRemoteHttpUrl("https://exa mple.com"));
        assertFalse(WebViewNavigationPolicy.isRemoteHttpUrl("not a url"));
    }
}
