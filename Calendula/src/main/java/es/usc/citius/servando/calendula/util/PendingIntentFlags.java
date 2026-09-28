package es.usc.citius.servando.calendula.util;

import android.app.PendingIntent;

/**
 * PendingIntent flag helpers for the app's Android 6.0+ baseline.
 */
public final class PendingIntentFlags {

    private PendingIntentFlags() {
    }

    public static int immutable(int baseFlags) {
        return baseFlags | PendingIntent.FLAG_IMMUTABLE;
    }
}
