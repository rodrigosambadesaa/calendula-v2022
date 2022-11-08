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

package es.usc.citius.servando.calendula.healthcareprovider.util

import android.content.Context
import es.usc.citius.servando.calendula.R
import org.dmfs.rfc5545.recur.Freq
import org.hl7.fhir.dstu3.model.Timing


object StringUtils {

    /**
     * Returns a string representation of a time unit
     */
    @JvmStatic
    fun timeUnitDisplayName(c: Context, u: Timing.UnitsOfTime) =
        freqDisplayName(c, TimingUtils.freqFromUnitsOfTime(u))

    /**
     * Returns a string representation of a recurrence frequency
     */
    @JvmStatic
    fun freqDisplayName(c: Context, f: Freq): String {
        return when (f) {
            Freq.WEEKLY -> c.resources.getString(R.string.weeks)
            Freq.DAILY -> c.resources.getString(R.string.days)
            Freq.HOURLY -> c.resources.getString(R.string.hours)
            Freq.MONTHLY -> c.resources.getString(R.string.months)
            Freq.MINUTELY -> c.resources.getString(R.string.minutes)
            Freq.SECONDLY -> c.resources.getString(R.string.seconds_label)
            Freq.YEARLY -> c.resources.getString(R.string.years)
            else -> throw IllegalArgumentException("Unsupported frequency " + f)
        }
    }
}