/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Disposable two-page PDFs; real Android PdfDocument/PdfRenderer, no provider data. */
@RunWith(AndroidJUnit4.class)
public class PrivatePdfPageSessionSmokeTest {
    @Test
    public void readsEveryPageAndUnlinksTemporaryPdfBeforeNavigating() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File file = new File(context.getCacheDir(),
                "extrainfo-paginated-ci-" + System.nanoTime() + ".pdf");
        PdfDocument document = new PdfDocument();
        try {
            for (int pageIndex = 0; pageIndex < 2; pageIndex++) {
                PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(
                        240, 320, pageIndex + 1).create();
                PdfDocument.Page page = document.startPage(info);
                Paint background = new Paint();
                background.setColor(pageIndex == 0 ? Color.BLUE : Color.RED);
                page.getCanvas().drawRect(0, 0, 240, 320, background);
                document.finishPage(page);
            }
            try (FileOutputStream out = new FileOutputStream(file)) {
                document.writeTo(out);
            }
        } finally {
            document.close();
        }

        try (PrivatePdfPageSession session = PrivatePdfPageSession.open(
                context.getCacheDir(), file)) {
            assertFalse("Temporary source must be unlinked while viewer is open", file.exists());
            assertEquals(2, session.pageCount());
            PrivatePdfPageSession.RenderedPage first = session.render(0);
            PrivatePdfPageSession.RenderedPage second = session.render(1);
            assertEquals(0, first.index);
            assertEquals(1, second.index);
            assertEquals(2, first.total);
            assertEquals(2, second.total);
            assertTrue(first.imageUrl.startsWith("data:image/png;base64,"));
            assertTrue(second.imageUrl.startsWith("data:image/png;base64,"));
            assertNotEquals("Independent PDF pages must not share decoded content",
                    first.imageUrl, second.imageUrl);
            assertTrue(first.imageUrl.length() < PrivatePdfPageSession.MAX_PNG_BYTES * 2);
            assertTrue(second.imageUrl.length() < PrivatePdfPageSession.MAX_PNG_BYTES * 2);
            try {
                session.render(2);
                fail("Out-of-range page must fail closed");
            } catch (IOException expected) {
                // No implicit wrap to the wrong clinical document page.
            }
        } finally {
            file.delete();
        }
    }

    @Test
    public void malformedPrivatePdfMustNotLeaveDownloadedDataBehind() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File file = new File(context.getCacheDir(),
                "extrainfo-malformed-ci-" + System.nanoTime() + ".pdf");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(new byte[]{1, 2, 3, 4, 5, 6});
        }
        try {
            PrivatePdfPageSession.open(context.getCacheDir(), file);
            fail("Malformed PDF must not be displayed");
        } catch (IOException expected) {
            assertFalse("Malformed temporary PDF must be deleted", file.exists());
        } finally {
            file.delete();
        }
    }
}
