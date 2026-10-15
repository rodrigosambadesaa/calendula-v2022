/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import android.net.Uri;

import java.util.Locale;

/** Recover valid local Android ringtone URIs without loading remote content. */
public final class NotificationSoundPolicy {
    private static final int MAX_SAVED_TONE_LENGTH = 2048;

    private NotificationSoundPolicy() {
    }

    public static Uri safeNotificationTone(String stored, Uri fallback) {
        if (stored == null || stored.isEmpty()
                || stored.length() > MAX_SAVED_TONE_LENGTH) {
            return fallback;
        }
        // Android ringtone references are local content or packaged-resource
        // URIs; legacy file URIs remain supported for restored backups.
        // Remote network URLs and script/data schemes are not ringtones.
        for (int i = 0; i < stored.length(); i++) {
            if (Character.isISOControl(stored.charAt(i))) {
                return fallback;
            }
        }
        try {
            Uri candidate = Uri.parse(stored);
            String scheme = candidate.getScheme();
            if (scheme == null) {
                return fallback;
            }
            scheme = scheme.toLowerCase(Locale.ROOT);
            if ("content".equals(scheme) || "android.resource".equals(scheme)) {
                return candidate.getAuthority() != null
                        && !candidate.getAuthority().isEmpty() ? candidate : fallback;
            }
            if ("file".equals(scheme)) {
                return candidate.getPath() != null
                        && !candidate.getPath().isEmpty() ? candidate : fallback;
            }
        } catch (RuntimeException invalid) {
            // A corrupted preference must not prevent notification delivery.
        }
        return fallback;
    }
}
