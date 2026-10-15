/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

import static org.junit.Assert.assertNotNull;

/** Decode shipped assets on real WebP-capable Android 6 through 17 devices. */
@RunWith(AndroidJUnit4.class)
public class HomeBackgroundAssetsSmokeTest {
    @Test
    public void everySelectableHomeBackgroundIsPresentAndDecodable() {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        for (int id = 1; id <= HomeProfileMgr.BG_COUNT; id++) {
            String path = String.format(Locale.ROOT, HomeProfileMgr.BG_IMAGE_PATTERN, id);
            Bitmap decoded = HomeProfileMgr.getBitmapFromAsset(context, path);
            assertNotNull("Missing or invalid bundled home image " + path, decoded);
            decoded.recycle();
        }
    }
}
