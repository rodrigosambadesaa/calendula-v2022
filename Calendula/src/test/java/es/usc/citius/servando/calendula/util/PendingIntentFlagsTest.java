package es.usc.citius.servando.calendula.util;

import android.app.PendingIntent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 23)
public class PendingIntentFlagsTest {

    @Test
    public void api23AddsImmutableFlag() {
        assertEquals(
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE,
                PendingIntentFlags.immutable(PendingIntent.FLAG_UPDATE_CURRENT));
    }

    @Test
    public void api23CanCreateImmutableOnlyFlags() {
        assertEquals(
                PendingIntent.FLAG_IMMUTABLE,
                PendingIntentFlags.immutable(0));
    }
}
