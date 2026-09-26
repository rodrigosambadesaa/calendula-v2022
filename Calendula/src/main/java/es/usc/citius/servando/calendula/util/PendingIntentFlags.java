package es.usc.citius.servando.calendula.util;

import android.app.PendingIntent;
import android.os.Build;

/**
 * PendingIntent flag helpers that preserve Calendula's API 18 compatibility while
 * keeping intents immutable on platforms that support the immutable flag.
 */
public final class PendingIntentFlags {

    private PendingIntentFlags() {
    }

    public static int immutable(int baseFlags) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return baseFlags | PendingIntent.FLAG_IMMUTABLE;
        }
        return baseFlags;
    }
}
