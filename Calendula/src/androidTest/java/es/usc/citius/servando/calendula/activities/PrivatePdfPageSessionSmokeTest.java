/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Disposable two-page PDFs; real Android PdfRenderer, no provider data. */
@RunWith(AndroidJUnit4.class)
public class PrivatePdfPageSessionSmokeTest {

    /**
     * The synthetic fixture is a deliberately minimal, standards-compliant
     * PDF 1.4 with two solid-color pages and explicit xref offsets. Do not
     * generate it with android.graphics.pdf.PdfDocument: Android 6's native
     * PDF creation/teardown produced a libpdfium SIGSEGV in our first CI run,
     * masking the behavior of provider-supplied PDFs.
     */
    private static byte[] independentTwoPagePdf() throws IOException {
        String bluePage = "0 0 1 rg\n0 0 240 320 re\nf\n";
        String redPage = "1 0 0 rg\n0 0 240 320 re\nf\n";
        String[] objects = {
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 240 320] /Contents 5 0 R >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 240 320] /Contents 6 0 R >>",
                "<< /Length " + bluePage.length() + " >>\nstream\n"
                        + bluePage + "endstream",
                "<< /Length " + redPage.length() + " >>\nstream\n"
                        + redPage + "endstream"
        };
        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        writeAscii(pdf, "%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.length; i++) {
            offsets.add(pdf.size());
            writeAscii(pdf, (i + 1) + " 0 obj\n" + objects[i] + "\nendobj\n");
        }
        int xrefOffset = pdf.size();
        writeAscii(pdf, "xref\n0 7\n0000000000 65535 f \n");
        for (int offset : offsets) {
            writeAscii(pdf, String.format(Locale.US, "%010d 00000 n \n", offset));
        }
        writeAscii(pdf, "trailer\n<< /Size 7 /Root 1 0 R >>\nstartxref\n"
                + xrefOffset + "\n%%EOF\n");
        return pdf.toByteArray();
    }

    private static void writeAscii(ByteArrayOutputStream output, String text) throws IOException {
        output.write(text.getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    public void readsEveryPageAndUnlinksTemporaryPdfBeforeNavigating() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File file = new File(context.getCacheDir(),
                "extrainfo-paginated-ci-" + System.nanoTime() + ".pdf");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(independentTwoPagePdf());
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
                // No implicit wrap to another clinical document page.
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
