package es.usc.citius.servando.calendula.activities;

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
}
