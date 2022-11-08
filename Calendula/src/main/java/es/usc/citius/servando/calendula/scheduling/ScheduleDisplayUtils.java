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
 *    along with this software.  If not, see <http://www.gnu.org/licenses>.
 */

package es.usc.citius.servando.calendula.scheduling;

import android.content.Context;
import android.content.res.Resources;

import java.util.ArrayList;

import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.Strings;


/**
 *
 */
public class ScheduleDisplayUtils {

    private static final String TAG = "ScheduleDisplayUtils";

    public static String getTimesByDayStr(int items, Context ctx) {
        switch (items) {
            case 0:
                return ctx.getString(R.string.never);
            case 1:
                return ctx.getString(R.string.once_a_day);
            case 2:
                return ctx.getString(R.string.twice_a_day);
            case 3:
                return ctx.getString(R.string.tree_times_a_day);
            case 4:
                return ctx.getString(R.string.four_times_a_day);
            default:
                return items + " " + ctx.getString(R.string.times_a_day);
        }
    }

    public static String getByDayStr(boolean[] checkedDays, Context ctx) {

        String[] days = getCheckedDayNames(checkedDays, ctx);

        if (days.length == 7 || days.length == 0) {
            return ctx.getString(R.string.every_day);
        }

        String dayStr = "";
        for (int i = 0; i < days.length - 1; i++) {
            if (i > 0) {
                dayStr += ", ";
            }
            dayStr += days[i];
        }
        return dayStr + ((days.length > 1 ? " " + ctx.getString(R.string.and) + " " : "") + days[days.length - 1]);
    }

    public static String[] getCheckedDayNames(boolean[] days, Context ctx) {
        String[] dayNames = ctx.getResources().getStringArray(R.array.day_names);
        ArrayList<String> checkedDays = new ArrayList<>();
        for (int i = 0; i < days.length; i++) {
            if (days[i]) {
                checkedDays.add(dayNames[i]);
            }
        }
        return checkedDays.toArray(new String[checkedDays.size()]);
    }

    public static String displayDose(final double dose, final Presentation presentation, final Resources resources) {

        StringBuilder display = new StringBuilder();

        // dosage
        if (!presentation.equals(Presentation.PILLS)) {
            display.append(Strings.prettyDouble(dose));
        } else {

            final int integerPart = (int) dose;
            final double decimalPart = dose - integerPart;

            final String integerString = Integer.toString(integerPart);
            String fractionString = "";

            if (decimalPart == 0.125) {
                fractionString = "1/8";
            } else if (decimalPart == 0.25) {
                fractionString = "1/4";
            } else if (decimalPart == 0.5) {
                fractionString = "1/2";
            } else if (decimalPart == 0.75) {
                fractionString = "3/4";
            } else {
                LogUtil.d(TAG, "displayDose: invalid decimalPart value! value = " + decimalPart);
            }

            if (integerPart > 0) {
                display.append(integerString);
                if (!fractionString.isEmpty()) {
                    display.append("+");
                }
            }
            if (!fractionString.isEmpty()) {
                display.append(fractionString);
            }
        }
        // units
        display.append(" ")
                .append(presentation.units(resources, dose));

        return display.toString();
    }
}
