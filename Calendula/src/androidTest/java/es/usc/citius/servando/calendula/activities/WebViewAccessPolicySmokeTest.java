/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import android.content.Context;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/** Runs against an actual installed WebView on Android API 23/33/36/37. */
@RunWith(AndroidJUnit4.class)
public class WebViewAccessPolicySmokeTest {
    @SuppressWarnings("deprecation")
    @Test
    public void unsafeWebViewOptionsAreRevokedBeforeDocumentRendering() {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            WebView webView = new WebView(context);
            try {
                WebSettings settings = webView.getSettings();
                // Deliberately emulate an unsafe default or an older viewer
                // configuration; no network, patient data or URLs are loaded.
                settings.setAllowFileAccess(true);
                settings.setAllowContentAccess(true);
                settings.setAllowFileAccessFromFileURLs(true);
                settings.setAllowUniversalAccessFromFileURLs(true);
                settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

                WebViewAccessPolicy.harden(settings);

                assertFalse(settings.getAllowFileAccess());
                assertFalse(settings.getAllowContentAccess());
                assertFalse(settings.getAllowFileAccessFromFileURLs());
                assertFalse(settings.getAllowUniversalAccessFromFileURLs());
                assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW,
                        settings.getMixedContentMode());
            } finally {
                webView.destroy();
            }
        });
    }
}
