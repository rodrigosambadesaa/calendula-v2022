/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import android.webkit.WebSettings;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Device-resource access is never a feature of a medical document viewer. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class WebViewAccessPolicyTest {
    @Test
    public void cachedAndNetworkContentMustNotAccessDeviceFilesOrMixedHttp() {
        WebSettings settings = mock(WebSettings.class);
        WebViewAccessPolicy.harden(settings);

        verify(settings).setAllowFileAccess(false);
        verify(settings).setAllowContentAccess(false);
        verify(settings).setAllowFileAccessFromFileURLs(false);
        verify(settings).setAllowUniversalAccessFromFileURLs(false);
        verify(settings).setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    }
}
