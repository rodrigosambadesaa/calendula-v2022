/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

/**
 * Single source of truth for reminder timing. Corrupted/legacy SharedPreferences
 * must not crash medication scheduling or overflow arithmetic.
 */
public final class ReminderTiming {

    private static final int DEFAULT_WINDOW_MINUTES = 120; // pref_notifications.xml
    private static final int DEFAULT_REPEAT_MINUTES = 15;
    private static final int MAX_MINUTES = 24 * 60;

    private ReminderTiming() {
    }

    public static int windowMinutes() {
        return parseWindowMinutes(readSavedMinutes(
                PreferenceKeys.SETTINGS_ALARM_REMINDER_WINDOW,
                DEFAULT_WINDOW_MINUTES));
    }

    /** Repeat interval shared by AlarmManager and notification text. */
    public static int repeatMinutes() {
        return parseRepeatMinutes(readSavedMinutes(
                PreferenceKeys.SETTINGS_ALARM_REPEAT_FREQUENCY,
                DEFAULT_REPEAT_MINUTES));
    }

    /**
     * A restored preference may contain a Boolean or Integer where Android
     * expects a String. In that case SharedPreferences#getString throws before
     * our numeric validation runs; failing here drops real medication alarms.
     * Retain the configured value if correctly typed and fall back to the
     * existing bounded default otherwise. Do not overwrite the user's data.
     */
    private static String readSavedMinutes(PreferenceKeys key, int fallback) {
        String defaultValue = Integer.toString(fallback);
        try {
            return PreferenceUtils.getString(key, defaultValue);
        } catch (ClassCastException incompatible) {
            return defaultValue;
        }
    }

    static int repeatSeconds() {
        return repeatMinutes() * 60; // Bounded at one day; cannot overflow.
    }

    static int parseWindowMinutes(String raw) {
        return parseBoundedMinutes(raw, DEFAULT_WINDOW_MINUTES, 0);
    }

    static int parseRepeatMinutes(String raw) {
        return parseBoundedMinutes(raw, DEFAULT_REPEAT_MINUTES, 1);
    }

    private static int parseBoundedMinutes(String raw, int fallback, int min) {
        if (raw == null) {
            return fallback;
        }
        try {
            int minutes = Integer.parseInt(raw.trim());
            return minutes >= min && minutes <= MAX_MINUTES ? minutes : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
