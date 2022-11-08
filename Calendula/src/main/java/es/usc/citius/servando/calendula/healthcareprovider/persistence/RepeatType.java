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

package es.usc.citius.servando.calendula.healthcareprovider.persistence;


import android.content.Context;

import org.hl7.fhir.dstu3.model.Timing;
import org.hl7.fhir.exceptions.FHIRException;

import es.usc.citius.servando.calendula.R;

/**
 * Dosage types
 */
public enum RepeatType {

    /**
     * Used when a precise time is supplied (Ex: 08:00)
     */
    TIME_OF_DAY("TIME_OF_DAY"),

    /**
     * Used with estimated dosages with a duration period
     */
    DURATION("DURATION"),

    /**
     * Used with general dosages to specify the period between repetitions
     */
    PERIOD("PERIOD"),

    /**
     * Used with dosages that are linked to daily events like meals
     */
    CM("CM"),   // CM	event occurs at breakfast
    CD("CD"),   // CD	event occurs at lunch
    CV("CV"),   // CV	event occurs at dinner
    ACM("ACM"), // ACM	event occurs [offset] before breakfast
    ACD("ACD"), // ACD	event occurs [offset] before lunch
    ACV("ACV"), // ACV	event occurs [offset] before dinner
    PCM("PCM"), // PCM	event occurs [offset] after breakfast
    PCD("PCD"), // PCD	event occurs [offset] after lunch
    PCV("PCV"); // PCV	event occurs [offset] after dinner

    private final String name;

    RepeatType(String s) {
        name = s;
    }

    public static RepeatType fromEventTiming(Timing.EventTiming timing) {
        String code = timing.toCode();
        switch (code) {
            case "CM":
                return CM;
            case "CD":
                return CD;
            case "CV":
                return CV;
            case "ACM":
                return ACM;
            case "ACD":
                return ACD;
            case "ACV":
                return PCV;
            case "PCM":
                return PCM;
            case "PCD":
                return PCD;
            case "PCV":
                return PCV;
            default:
                throw new IllegalArgumentException("Unsupported EventTiming value: " + code);
        }
    }

    public String toString() {
        return this.name;
    }

    public String code() {
        return toString();
    }

    public Timing.EventTiming toEventTiming() throws FHIRException {
        return Timing.EventTiming.fromCode(code());
    }

    /**
     * Check if this repeat type is associated with a meal time, be it with, before or after the meal.
     *
     * @return <code>true</code> if the above conditions are met, <code>false</code> otherwise.
     */
    public boolean isMeal() {
        switch (this) {
            case CM:
            case CD:
            case CV:
            case ACM:
            case ACD:
            case ACV:
            case PCM:
            case PCD:
            case PCV:
                return true;
            default:
                return false;
        }
    }

    /**
     * Gives the name of the associated meal if this repeat type has one.
     *
     * @param context a {@link Context}
     * @return the name of the associated meal, <code>null</code> if there is not any.
     * @see #isMeal()
     */
    public String mealName(final Context context) {
        switch (this) {
            case CM:
            case ACM:
            case PCM:
                return context.getString(R.string.routine_breakfast).toLowerCase();
            case CD:
            case ACD:
            case PCD:
                return context.getString(R.string.routine_lunch).toLowerCase();
            case CV:
            case ACV:
            case PCV:
                return context.getString(R.string.routine_dinner).toLowerCase();
            default:
                return null;

        }
    }

    /**
     * Gives the contextualized string for this repeat type when it's associated with a meal.
     * For example, for <code>ACM</code>, this method will return "Before breakfast".
     *
     * @param context a {@link Context}
     * @return the contextualized string for this repeat type, <code>null</code> if this is not a meal repeat type;
     * @see #isMeal()
     * @see #mealName(Context)
     */
    public String getMealContextString(final Context context) {
        switch (this) {
            // with a meal
            case CM:
                return context.getString(R.string.with_breakfast);
            case CD:
                return context.getString(R.string.with_lunch);
            case CV:
                return context.getString(R.string.with_dinner);
            // before a meal
            case ACM:
                return context.getString(R.string.before_breakfast);
            case ACD:
                return context.getString(R.string.before_lunch);
            case ACV:
                return context.getString(R.string.before_dinner);
            // after a meal
            case PCM:
                return context.getString(R.string.after_breakfast);
            case PCD:
                return context.getString(R.string.after_lunch);
            case PCV:
                return context.getString(R.string.after_dinner);
            default:
                return null;
        }
    }

}
