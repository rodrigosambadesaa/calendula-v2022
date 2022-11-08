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

import java.util.Comparator;

import es.usc.citius.servando.calendula.persistence.Routine;
import es.usc.citius.servando.calendula.scheduling.model.recur.DailyFixedTime;


public class Comparators {

    public static Comparator<DailyFixedTime> DAILY_FIXED_TIME  = new Comparator<DailyFixedTime>() {
        @Override
        public int compare(DailyFixedTime o1, DailyFixedTime o2) {
            return o1.getTime().compareTo(o2.getTime());
        }
    };


    public static Comparator<Routine> ROUTINE = new Comparator<Routine>() {
        @Override
        public int compare(Routine o1, Routine o2) {
            return o1.getTime().compareTo(o2.getTime());
        }
    };
}
