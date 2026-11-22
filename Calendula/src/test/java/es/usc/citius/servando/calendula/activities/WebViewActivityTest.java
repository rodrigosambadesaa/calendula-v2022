package es.usc.citius.servando.calendula.activities;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class WebViewActivityTest {

    @Test
    public void readFullyDoesNotRelyOnAvailableByteCount() throws Exception {
        final byte[] expected = "complete-css-payload".getBytes("UTF-8");
        ByteArrayInputStream stream = new ByteArrayInputStream(expected) {
            @Override
            public synchronized int available() {
                return 1;
            }
        };

        assertArrayEquals(expected, WebViewActivity.readFully(stream));
    }

    @Test
    public void cssReaderKeepsBytesAfterTemporaryZeroRead() throws Exception {
        byte[] content = "body{color:black}".getBytes("UTF-8");
        InputStream interrupted = new ByteArrayInputStream(content) {
            boolean first = true;
            @Override
            public synchronized int read(byte[] bytes, int offset, int length) {
                if (first) {
                    first = false;
                    return 0;
                }
                return super.read(bytes, offset, length);
            }
        };
        assertArrayEquals(content, WebViewActivity.readFully(interrupted, content.length));
    }

    @Test
    public void cssReaderNeverAllocatesBeyondByteBudget() throws Exception {
        byte[] oversized = new byte[17];
        try {
            WebViewActivity.readFully(new ByteArrayInputStream(oversized), 16);
            fail("CSS document exceeds the configured maximum");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("byte budget"));
        }
        byte[] withinLimit = new byte[16];
        assertArrayEquals(withinLimit,
                WebViewActivity.readFully(new ByteArrayInputStream(withinLimit), 16));
    }

    @Test
    public void permanentlyStalledCssStreamFailsWithoutBusyLoop() throws Exception {
        InputStream stream = new InputStream() {
            @Override
            public int read() {
                return 0;
            }
            @Override
            public int read(byte[] bytes, int offset, int len) {
                return 0;
            }
        };
        try {
            WebViewActivity.readFully(stream, 100);
            fail("No-progress stream must stop with a controlled failure");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("no progress"));
        }
    }

    @Test
    public void cssReaderRejectsBudgetsLargerThanItsGlobalLimit() throws Exception {
        try {
            WebViewActivity.readFully(new ByteArrayInputStream(new byte[0]),
                    WebViewActivity.MAX_CSS_ASSET_BYTES + 1);
            fail("A request must not override the global CSS size limit");
        } catch (IllegalArgumentException expected) {
            // Guard is enforced before reading the stream.
        }
    }

    @Test
    public void cssOverridesAreLiteralNotRegularExpressions() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("a.b", "$5");

        assertEquals("$5 aXb", WebViewActivity.applyCssOverrides("a.b aXb", overrides));
    }
    @Test
    public void oversizedCssOverrideIsRejectedBeforeBuildingExpandedString() {
        Map<String, String> replacements = new HashMap<>();
        char[] large = new char[WebViewActivity.MAX_CSS_ASSET_BYTES];
        java.util.Arrays.fill(large, 'Z');
        replacements.put("x", new String(large));
        try {
            WebViewActivity.applyCssOverrides("x x", replacements);
            fail("Injected CSS must not grow beyond the resource budget");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("size budget"));
        }
    }

    @Test
    public void emptyCssReplacementKeysAreIgnoredWithoutUnboundedExpansion() {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("", "many");
        assertEquals("body{}", WebViewActivity.applyCssOverrides("body{}", replacements));
    }

    @Test
    public void externalBrowserHandoffDoesNotCrashWithoutHandlerOrPermission() {
        Context context = mock(Context.class);
        Intent target = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://example.test/synthetic-leaflet"));
        assertTrue(WebViewActivity.tryStartExternalActivity(context, target));
        verify(context).startActivity(target);

        doThrow(new ActivityNotFoundException("synthetic no browser"))
                .when(context).startActivity(target);
        assertFalse(WebViewActivity.tryStartExternalActivity(context, target));

        doThrow(new SecurityException("synthetic profile policy"))
                .when(context).startActivity(target);
        assertFalse(WebViewActivity.tryStartExternalActivity(context, target));
    }

}
