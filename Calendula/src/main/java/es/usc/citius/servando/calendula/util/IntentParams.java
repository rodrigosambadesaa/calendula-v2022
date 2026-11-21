/*
 *    Calendula - An assistant for personal medication management.
 *    Copyright (C) 2016 CITIUS - USC
 *
 *    Calendula is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this software.  If not, see <http://www.gnu.org/licenses/>.
 */

package es.usc.citius.servando.calendula.util;

import es.usc.citius.servando.calendula.BuildConfig;

/**
 *
 */
public class IntentParams {

    // prefix for all actions and extras
    public static final String ID = BuildConfig.APPLICATION_ID;

    public static final int DAILY_UPDATE_ID = 10001;

    // Intent extras
    public static final String EXTRA_ACTION = extra("ACTION");
    public static final String EXTRA_REMINDER_ID = extra("REMINDER_ID");
    public static final String EXTRA_ALARM_INSTANCE = extra("ALARM_INSTANCE");
    public static final String EXTRA_INTAKE_SNAPSHOT = extra("INTAKE_SNAPSHOT");
    public static final String EXTRA_DATETIME = extra("DATETIME");
    public static final String EXTRA_TIME = extra("TIME");
    public static final String EXTRA_PATIENT_ID = extra("PATIENT_ID");
    public static final String EXTRA_POSITION = extra("POSITION");
    public static final String EXTRA_ACTIVEMED_ID = extra("ACTIVEMED_ID");

    // Intent extra values
    public static final String ACTION_ALARM_REMINDER = action("ALARM_REMINDER");
    public static final String ACTION_DAILY_UPDATE = action("DAILY_UPDATE");
    public static final String ACTION_ALARM_CANCEL = action("ALARM_CANCEL");
    public static final String ACTION_ALARM_DELAY = action("ALARM_DELAY");
    public static final String ACTION_ALARM_CONFIRM = action("ALARM_CONFIRM");
    public static final String ACTION_CREATE_REMINDER = action("CREATE_REMINDER");
    public static final String ACTION_SHOW_ACTIVE_MEDS = action("SHOW_ACTIVE_MEDS");

    /**
     * The UUID is an opaque per-reminder instance identifier, never a patient
     * code, drug name or timestamp. Require the canonical random UUID shape
     * before constructing/accepting OS alarm identities.
     */
    public static boolean isValidAlarmInstance(String instance) {
        return instance != null && instance.matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    }

    private static final String extra(String value) {
        return ID + ".EXTRA_" + value;
    }

    private static final String action(String value) {
        return ID + ".ACTION_" + value;
    }
}
