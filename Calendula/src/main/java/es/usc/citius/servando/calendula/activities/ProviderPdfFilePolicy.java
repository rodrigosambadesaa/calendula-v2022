/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import java.io.File;
import java.io.IOException;

/**
 * Provider downloads are private temporary files created by ExtraInfoHelper.
 * A stale or spoofed EventBus file URL must never read or delete arbitrary
 * application files, including files reached through symlinks.
 */
final class ProviderPdfFilePolicy {
    private ProviderPdfFilePolicy() {
    }

    static boolean isTrustedPdf(File appCacheDirectory, File candidate) {
        if (appCacheDirectory == null || candidate == null || !candidate.isFile()) {
            return false;
        }
        try {
            File parent = appCacheDirectory.getCanonicalFile();
            File file = candidate.getCanonicalFile();
            String name = file.getName();
            return parent.equals(file.getParentFile())
                    && name.startsWith("extrainfo")
                    && name.endsWith(".pdf")
                    && name.length() > "extrainfo.pdf".length();
        } catch (IOException | SecurityException malformed) {
            return false;
        }
    }
}
