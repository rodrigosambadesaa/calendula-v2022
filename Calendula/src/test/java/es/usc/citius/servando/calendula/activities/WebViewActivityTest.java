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
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
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
    public void cssOverridesAreLiteralNotRegularExpressions() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("a.b", "$5");

        assertEquals("$5 aXb", WebViewActivity.applyCssOverrides("a.b aXb", overrides));
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
