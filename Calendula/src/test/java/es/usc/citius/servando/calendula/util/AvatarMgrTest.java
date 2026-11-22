/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;

import es.usc.citius.servando.calendula.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 27)
public class AvatarMgrTest {

    @Test
    public void unknownAvatarReturnsResolvedColorsNotResourceIds() {
        Resources resources = mock(Resources.class);
        when(resources.getColor(R.color.android_blue)).thenReturn(Color.BLUE);
        when(resources.getColor(R.color.android_blue_light)).thenReturn(Color.CYAN);

        assertArrayEquals(new int[]{Color.BLUE, Color.CYAN},
                AvatarMgr.colorsFor(resources, "synthetic-unknown-avatar"));
        assertArrayEquals(new int[]{Color.BLUE, Color.CYAN},
                AvatarMgr.colorsFor(resources, null));
    }

    @Test
    public void thumbnailIsRecycledAfterExtractingPalette() {
        Resources resources = RuntimeEnvironment.getApplication().getResources();
        Bitmap thumbnail = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
        thumbnail.eraseColor(Color.BLUE);
        assertFalse(thumbnail.isRecycled());

        int[] colors = AvatarMgr.paletteColorsAndRecycle(resources, thumbnail);

        assertEquals(2, colors.length);
        assertTrue("Synchronous palette thumbnail must be released", thumbnail.isRecycled());
        assertArrayEquals(AvatarMgr.defaultColors(resources),
                AvatarMgr.paletteColorsAndRecycle(resources, null));
    }

    @Test
    public void callerCannotModifyCachedAvatarColors() {
        Resources resources = RuntimeEnvironment.getApplication().getResources();
        int[] first = AvatarMgr.colorsFor(resources, AvatarMgr.DEFAULT_AVATAR);
        int[] second = AvatarMgr.colorsFor(resources, AvatarMgr.DEFAULT_AVATAR);
        assertNotSame(first, second);
        int expected = second[0];
        first[0] = expected ^ 0x00ffffff;
        assertEquals(expected, AvatarMgr.colorsFor(resources, AvatarMgr.DEFAULT_AVATAR)[0]);
    }
}
