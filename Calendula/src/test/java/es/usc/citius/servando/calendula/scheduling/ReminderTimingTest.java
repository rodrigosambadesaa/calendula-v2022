/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ReminderTimingTest {

    @Test
    public void acceptsCurrentReminderWindowAndRepeatChoices() {
        assertEquals(30, ReminderTiming.parseWindowMinutes("30"));
        assertEquals(60, ReminderTiming.parseWindowMinutes("60"));
        assertEquals(120, ReminderTiming.parseWindowMinutes("120"));
        assertEquals(15, ReminderTiming.parseRepeatMinutes("15"));
    }

    @Test
    public void corruptedWindowValuesUseSafeDefault() {
        assertEquals(120, ReminderTiming.parseWindowMinutes(null));
        assertEquals(120, ReminderTiming.parseWindowMinutes(""));
        assertEquals(120, ReminderTiming.parseWindowMinutes("-5"));
        assertEquals(120, ReminderTiming.parseWindowMinutes("9999999999999999"));
        assertEquals(120, ReminderTiming.parseWindowMinutes("not a number"));
        assertEquals(120, ReminderTiming.parseWindowMinutes("1441"));
    }

    @Test
    public void invalidRepeatFrequencyCannotOverflowOrDisableReminders() {
        assertEquals(15, ReminderTiming.parseRepeatMinutes("0"));
        assertEquals(15, ReminderTiming.parseRepeatMinutes("-1"));
        assertEquals(15, ReminderTiming.parseRepeatMinutes("99999999999999999"));
        assertEquals(15, ReminderTiming.parseRepeatMinutes(null));
        assertEquals(15, ReminderTiming.parseRepeatMinutes("1441"));
    }

    @Test
    public void upperBoundsAreWithinIntegerAndJodaMinuteArithmetic() {
        assertEquals(1440, ReminderTiming.parseWindowMinutes("1440"));
        assertEquals(1440, ReminderTiming.parseRepeatMinutes("1440"));
        assertEquals(0, ReminderTiming.parseWindowMinutes("0"));
        assertEquals(120, ReminderTiming.parseWindowMinutes(" 120 "));
    }

    @Test
    public void wrongTypeWindowPreferenceCannotCrashMedicationScheduling() {
        try (org.mockito.MockedStatic<es.usc.citius.servando.calendula.util.PreferenceUtils> prefs =
                     org.mockito.Mockito.mockStatic(
                             es.usc.citius.servando.calendula.util.PreferenceUtils.class)) {
            prefs.when(() -> es.usc.citius.servando.calendula.util.PreferenceUtils.getString(
                    es.usc.citius.servando.calendula.util.PreferenceKeys.SETTINGS_ALARM_REMINDER_WINDOW,
                    "120")).thenThrow(new ClassCastException("Restored integer"));
            assertEquals(120, ReminderTiming.windowMinutes());
        }
    }

    @Test
    public void wrongTypeRepeatPreferenceCannotCrashMedicationNotifications() {
        try (org.mockito.MockedStatic<es.usc.citius.servando.calendula.util.PreferenceUtils> prefs =
                     org.mockito.Mockito.mockStatic(
                             es.usc.citius.servando.calendula.util.PreferenceUtils.class)) {
            prefs.when(() -> es.usc.citius.servando.calendula.util.PreferenceUtils.getString(
                    es.usc.citius.servando.calendula.util.PreferenceKeys.SETTINGS_ALARM_REPEAT_FREQUENCY,
                    "15")).thenThrow(new ClassCastException("Restored boolean"));
            assertEquals(15, ReminderTiming.repeatMinutes());
            assertEquals(900, ReminderTiming.repeatSeconds());
        }
    }

    @Test
    public void correctlyTypedSavedReminderSettingsKeepTheirUserSpecifiedValues() {
        try (org.mockito.MockedStatic<es.usc.citius.servando.calendula.util.PreferenceUtils> prefs =
                     org.mockito.Mockito.mockStatic(
                             es.usc.citius.servando.calendula.util.PreferenceUtils.class)) {
            prefs.when(() -> es.usc.citius.servando.calendula.util.PreferenceUtils.getString(
                    es.usc.citius.servando.calendula.util.PreferenceKeys.SETTINGS_ALARM_REMINDER_WINDOW,
                    "120")).thenReturn("60");
            prefs.when(() -> es.usc.citius.servando.calendula.util.PreferenceUtils.getString(
                    es.usc.citius.servando.calendula.util.PreferenceKeys.SETTINGS_ALARM_REPEAT_FREQUENCY,
                    "15")).thenReturn("30");
            assertEquals(60, ReminderTiming.windowMinutes());
            assertEquals(30, ReminderTiming.repeatMinutes());
            assertEquals(1800, ReminderTiming.repeatSeconds());
        }
    }
}
