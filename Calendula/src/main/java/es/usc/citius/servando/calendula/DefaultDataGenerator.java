/*
 *    Calendula - An assistant for personal medication management.
 *    Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
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

package es.usc.citius.servando.calendula;

import android.content.Context;
import android.content.res.Resources;

import org.joda.time.LocalTime;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Routine;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.RepeatType;

public class DefaultDataGenerator {

    private static final String TAG = "DefaultDataGenerator";

    public static void generateDefaultRoutines(Patient p, Context ctx) {
        Resources r = ctx.getResources();
        Routine breakfast = new Routine(p, new LocalTime(9, 0), r.getString(R.string.routine_breakfast));
        Routine lunch = new Routine(p, new LocalTime(13, 0), r.getString(R.string.routine_lunch));
        Routine dinner = new Routine(p, new LocalTime(21, 0), r.getString(R.string.routine_dinner));

        breakfast.setDailyEventType(RepeatType.CM);
        lunch.setDailyEventType(RepeatType.CD);
        dinner.setDailyEventType(RepeatType.CV);

        DB.routines().save(breakfast);
        DB.routines().save(lunch);
        DB.routines().save(dinner);
    }

}
