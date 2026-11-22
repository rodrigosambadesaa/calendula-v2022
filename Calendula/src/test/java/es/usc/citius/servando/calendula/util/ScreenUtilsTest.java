/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 27)
public class ScreenUtilsTest {

    @Test
    public void invalidPreviewSizesAreRejectedBeforeOpeningImageAssets() {
        Context context = mock(Context.class);
        assertNull(ScreenUtils.getResizedBitmap(context, "synthetic.png", 0, 100));
        assertNull(ScreenUtils.getResizedBitmap(context, "synthetic.png", 100, 0));
        assertNull(ScreenUtils.getResizedBitmap(context, "synthetic.png", -1, 100));
        assertNull(ScreenUtils.getResizedBitmap(context, "synthetic.png",
                Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertNull(ScreenUtils.getResizedBitmap(context, "", 100, 100));
        assertNull(ScreenUtils.getResizedBitmap(null, "synthetic.png", 100, 100));
        verifyNoInteractions(context);
    }

    @Test
    public void previewPixelBudgetCannotOverflowAndBoundaryIsInclusive() {
        assertTrue(ScreenUtils.isSafePreviewSize(2000, 2000));
        assertTrue(ScreenUtils.isSafePreviewSize(1, 4000000));
        assertFalse(ScreenUtils.isSafePreviewSize(2001, 2001));
        assertFalse(ScreenUtils.isSafePreviewSize(Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertFalse(ScreenUtils.isSafePreviewSize(1, 0));
    }

    @Test
    public void missingImageAssetReturnsNoBitmapInsteadOfCrashing() {
        Context context = RuntimeEnvironment.getApplication();
        assertNull(ScreenUtils.getResizedBitmap(context,
                "nonexistent-synthetic-clinical-preview-92458.png", 240, 240));
    }
}
