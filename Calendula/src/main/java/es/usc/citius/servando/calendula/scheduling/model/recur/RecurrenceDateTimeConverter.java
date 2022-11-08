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

package es.usc.citius.servando.calendula.scheduling.model.recur;

import org.joda.time.DateTime;


public class RecurrenceDateTimeConverter {

    public static final RecurrenceDateTimeConverter instance = new RecurrenceDateTimeConverter();

    public static RecurrenceDateTimeConverter instance(){
        return instance;
    }

    private RecurrenceDateTimeConverter(){}

    org.dmfs.rfc5545.DateTime convert(DateTime dt) {
        return new org.dmfs.rfc5545.DateTime(
                // date
                dt.getYear(), dt.getMonthOfYear() - 1, dt.getDayOfMonth(),
                // time
                dt.getHourOfDay(), dt.getMinuteOfHour(), dt.getSecondOfMinute());
    }

    DateTime convert(org.dmfs.rfc5545.DateTime dt) {
        return new DateTime(
                // date
                dt.getYear(), dt.getMonth() + 1, dt.getDayOfMonth(),
                // time
                dt.getHours(), dt.getMinutes(), dt.getSeconds());
    }

}
