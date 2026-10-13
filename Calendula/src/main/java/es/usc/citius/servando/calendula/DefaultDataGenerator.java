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

    /**
     * Provision breakfast/lunch/dinner at most once per patient. This is
     * called both from profile creation and authenticated bootstrap; a retry
     * must not duplicate existing default routines or reset user-edited times.
     * The three insertions commit together, including when only some were
     * missing in an older database.
     */
    public static void generateDefaultRoutines(Patient p, Context ctx) {
        if (p == null || p.getId() == null || p.getId() <= 0L
                || ctx == null || DB.patients().findById(p.getId()) == null) {
            throw new IllegalArgumentException("Default routines require a persisted patient");
        }
        final Patient owner = DB.patients().findById(p.getId());
        final Resources resources = ctx.getResources();
        DB.transaction(() -> {
            ensureDefaultRoutine(owner, RepeatType.CM, new LocalTime(9, 0),
                    resources.getString(R.string.routine_breakfast));
            ensureDefaultRoutine(owner, RepeatType.CD, new LocalTime(13, 0),
                    resources.getString(R.string.routine_lunch));
            ensureDefaultRoutine(owner, RepeatType.CV, new LocalTime(21, 0),
                    resources.getString(R.string.routine_dinner));
            return null;
        });
    }

    private static void ensureDefaultRoutine(Patient patient, RepeatType event,
                                             LocalTime fallbackTime, String displayName)
            throws java.sql.SQLException {
        if (DB.routines().findByPatientAndDailyEvent(patient, event) != null) {
            // Never overwrite the owner's existing meal name or clock time.
            return;
        }
        Routine created = new Routine(patient, fallbackTime, displayName);
        created.setDailyEventType(event);
        if (DB.routines().create(created) != 1 || created.getId() == null) {
            throw new java.sql.SQLException("Could not initialize patient meal routine");
        }
    }

}
