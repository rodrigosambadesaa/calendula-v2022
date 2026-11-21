/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

/**
 * Single-worker, one-page-at-a-time rendering of a provider document.
 *
 * Keep only the already-open PDF file descriptor; unlink the private
 * temporary file immediately after opening it. A process death consequently
 * cannot leave the downloaded PDF in the cache. Do not share this renderer
 * between threads or cache a list of PNGs.
 */
final class PrivatePdfPageSession implements AutoCloseable {
    static final int MAX_PAGES = 256;
    static final int MAX_PNG_BYTES = 20 * 1024 * 1024;

    private final PdfRenderer renderer;
    private final int pageCount;
    private boolean closed;

    private PrivatePdfPageSession(PdfRenderer renderer, int pageCount) {
        this.renderer = renderer;
        this.pageCount = pageCount;
    }

    static PrivatePdfPageSession open(File cacheDirectory, File source) throws IOException {
        if (!ProviderPdfFilePolicy.isTrustedPdf(cacheDirectory, source)) {
            throw new IOException("PDF source is not a trusted private temporary file");
        }
        ParcelFileDescriptor descriptor = null;
        PdfRenderer renderer = null;
        boolean transferred = false;
        try {
            descriptor = ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(descriptor);
            descriptor = null; // PdfRenderer owns and closes its descriptor.
            int count = renderer.getPageCount();
            if (count < 1 || count > MAX_PAGES) {
                throw new IOException("PDF page count outside safe preview limits");
            }
            // The verified private file must not persist while a viewer is open.
            // PdfRenderer reads the already-open descriptor after unlinking it.
            if (!ProviderPdfFilePolicy.isTrustedPdf(cacheDirectory, source)
                    || !source.delete()) {
                throw new IOException("Cannot safely erase temporary PDF after opening");
            }
            transferred = true;
            return new PrivatePdfPageSession(renderer, count);
        } catch (RuntimeException invalidPdf) {
            throw new IOException("Provider PDF could not be opened", invalidPdf);
        } finally {
            if (!transferred) {
                if (renderer != null) {
                    renderer.close();
                } else if (descriptor != null) {
                    descriptor.close();
                }
                // Attempt to clean up even malformed or oversized input.
                if (ProviderPdfFilePolicy.isTrustedPdf(cacheDirectory, source)) {
                    source.delete();
                }
            }
        }
    }

    int pageCount() {
        return pageCount;
    }

    RenderedPage render(int index) throws IOException {
        if (closed || index < 0 || index >= pageCount) {
            throw new IOException("PDF page not available");
        }
        try (PdfRenderer.Page page = renderer.openPage(index)) {
            int[] dimensions = PdfBitmapBounds.fit(page.getWidth(), page.getHeight());
            Bitmap bitmap = Bitmap.createBitmap(
                    dimensions[0], dimensions[1], Bitmap.Config.ARGB_8888);
            try {
                bitmap.eraseColor(Color.WHITE);
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                try (ByteArrayOutputStream png = new BoundedPngBuffer()) {
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, png)
                            || png.size() > MAX_PNG_BYTES) {
                        throw new IOException("PDF page exceeds bounded image encoding");
                    }
                    return new RenderedPage(index, pageCount,
                            "data:image/png;base64,"
                                    + Base64.encodeToString(png.toByteArray(), Base64.NO_WRAP));
                }
            } finally {
                bitmap.recycle();
            }
        } catch (RuntimeException invalidPage) {
            throw new IOException("PDF page could not be rendered", invalidPage);
        }
    }

    /**
     * Fail before ByteArrayOutputStream can grow beyond the PNG byte budget.
     * A post-compression size check alone would permit excessive transient
     * allocations for a hostile PDF on older, memory-constrained devices.
     */
    static final class BoundedPngBuffer extends ByteArrayOutputStream {
        private final int maxBytes;

        BoundedPngBuffer() {
            this(MAX_PNG_BYTES);
        }

        BoundedPngBuffer(int maxBytes) {
            if (maxBytes < 0 || maxBytes > MAX_PNG_BYTES) {
                throw new IllegalArgumentException("Invalid PDF PNG byte budget");
            }
            this.maxBytes = maxBytes;
        }

        private void requireSpace(int bytes) {
            if (bytes < 0 || bytes > maxBytes - count) {
                throw new IllegalStateException("Encoded PDF page exceeds byte limit");
            }
        }

        @Override
        public synchronized void write(int value) {
            requireSpace(1);
            super.write(value);
        }

        @Override
        public synchronized void write(byte[] data, int offset, int length) {
            if (data == null) {
                throw new NullPointerException("PNG data");
            }
            if (offset < 0 || length < 0 || offset > data.length - length) {
                throw new IndexOutOfBoundsException("Invalid PNG chunk");
            }
            requireSpace(length);
            super.write(data, offset, length);
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            renderer.close();
        }
    }

    static final class RenderedPage {
        final int index;
        final int total;
        final String imageUrl;

        RenderedPage(int index, int total, String imageUrl) {
            this.index = index;
            this.total = total;
            this.imageUrl = imageUrl;
        }
    }
}
