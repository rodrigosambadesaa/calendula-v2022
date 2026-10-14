/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import android.os.BadParcelableException;
import android.os.Parcel;

import org.joda.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class WebViewRequestTest {

    @Test
    public void defaultRequestDoesNotRequestCssOrJavaScript() {
        WebViewRequest request = new WebViewRequest("https://example.test/page");

        assertFalse(request.isJavaScriptEnabled());
        assertNull(request.getCustomCss());
        assertNotNull(request.getCustomCssOverrides());
    }

    @Test
    public void emptyCssListIsNormalizedToNoCss() {
        WebViewRequest request = new WebViewRequest("https://example.test/page");
        request.setCustomCss(Arrays.<String>asList());

        assertNull(request.getCustomCss());
    }

    @Test
    public void nonEmptyCssListRemainsRequested() {
        WebViewRequest request = new WebViewRequest("https://example.test/page");
        request.setCustomCss(Arrays.asList("prospect.css"));

        assertEquals(Arrays.asList("prospect.css"), request.getCustomCss());
    }

    @Test
    public void parcelRoundTripSupportsNullCacheTtlAndNoCss() {
        WebViewRequest request = new WebViewRequest("https://example.test/page");
        request.setCacheTTL(null);
        request.setCustomCssOverrides(null);

        Parcel parcel = Parcel.obtain();
        try {
            request.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);

            WebViewRequest restored = WebViewRequest.CREATOR.createFromParcel(parcel);
            assertNull(restored.getCustomCss());
            assertNull(restored.getCacheTTL());
            assertNotNull(restored.getCustomCssOverrides());
            assertEquals(0, restored.getCustomCssOverrides().size());
        } finally {
            parcel.recycle();
        }
    }

    @Test
    public void legacyUnknownCacheModeAndMalformedTtlRestoreSafely() {
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeString("https://example.test/synthetic-leaflet");
            for (int i = 0; i < 5; i++) {
                parcel.writeString(null);
            }
            parcel.writeByte((byte) 0);
            parcel.writeByte((byte) 0);
            parcel.writeString("REMOVED_LEGACY_CACHE_MODE");
            parcel.writeInt(0); // no CSS
            parcel.writeString("not-an-iso-duration");
            parcel.writeInt(0); // no overrides
            parcel.setDataPosition(0);

            WebViewRequest restored = WebViewRequest.CREATOR.createFromParcel(parcel);
            assertEquals(WebViewRequest.CacheType.NO_CACHE, restored.getCacheType());
            assertNull(restored.getCacheTTL());
            assertFalse(restored.isJavaScriptEnabled());
            assertFalse(restored.isExternalLinksEnabled());
        } finally {
            parcel.recycle();
        }
    }

    @Test
    public void nullCacheModeDoesNotCrashSerializationOrTurnOnExternalFeatures() {
        WebViewRequest request = new WebViewRequest("https://example.test/leaflet");
        request.setCacheType(null);
        assertEquals(WebViewRequest.CacheType.NO_CACHE, request.getCacheType());
        Parcel parcel = Parcel.obtain();
        try {
            request.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            WebViewRequest restored = WebViewRequest.CREATOR.createFromParcel(parcel);
            assertEquals(WebViewRequest.CacheType.NO_CACHE, restored.getCacheType());
            assertFalse(restored.isJavaScriptEnabled());
        } finally {
            parcel.recycle();
        }
    }

    @Test
    public void corruptHugeCssAndOverrideCountsFailBeforeAllocatingCollections() {
        for (int cssCount : new int[]{-1, 33, Integer.MAX_VALUE}) {
            Parcel parcel = newRequestParcel();
            try {
                parcel.writeInt(cssCount);
                parcel.setDataPosition(0);
                try {
                    WebViewRequest.CREATOR.createFromParcel(parcel);
                    org.junit.Assert.fail("Corrupt CSS count accepted: " + cssCount);
                } catch (BadParcelableException expected) {
                    // Fail closed without allocating a huge String array.
                }
            } finally {
                parcel.recycle();
            }
        }
        for (int overrideCount : new int[]{-1, 129, Integer.MAX_VALUE}) {
            Parcel parcel = newRequestParcel();
            try {
                parcel.writeInt(0); // CSS files
                parcel.writeString(null); // cache TTL
                parcel.writeInt(overrideCount);
                parcel.setDataPosition(0);
                try {
                    WebViewRequest.CREATOR.createFromParcel(parcel);
                    org.junit.Assert.fail("Corrupt override count accepted: " + overrideCount);
                } catch (BadParcelableException expected) {
                    // No attacker-sized HashMap iteration.
                }
            } finally {
                parcel.recycle();
            }
        }
    }

    private static Parcel newRequestParcel() {
        Parcel parcel = Parcel.obtain();
        parcel.writeString("https://example.test/leaflet");
        for (int i = 0; i < 5; i++) {
            parcel.writeString(null);
        }
        parcel.writeByte((byte) 0);
        parcel.writeByte((byte) 0);
        parcel.writeString("NO_CACHE");
        return parcel;
    }

    @Test
    public void parcelRoundTripPreservesCssAndTtl() {
        WebViewRequest request = new WebViewRequest("https://example.test/page");
        request.setCustomCss(Arrays.asList("prospect.css"));
        request.setCacheTTL(Duration.standardMinutes(5));

        Parcel parcel = Parcel.obtain();
        try {
            request.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);

            WebViewRequest restored = WebViewRequest.CREATOR.createFromParcel(parcel);
            assertEquals(Arrays.asList("prospect.css"), restored.getCustomCss());
            assertEquals(Duration.standardMinutes(5), restored.getCacheTTL());
        } finally {
            parcel.recycle();
        }
    }
}
