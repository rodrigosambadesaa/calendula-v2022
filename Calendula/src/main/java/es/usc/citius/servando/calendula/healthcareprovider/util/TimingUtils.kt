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

package es.usc.citius.servando.calendula.healthcareprovider.util

import org.dmfs.rfc5545.recur.Freq
import org.hl7.fhir.dstu3.model.Timing

object TimingUtils {

    /**
     * Converts from  FHIR UnitsOfTime to recurrence frequency
     */
    @JvmStatic
    fun freqFromUnitsOfTime(t: Timing.UnitsOfTime): Freq {
        return when (t) {
            Timing.UnitsOfTime.WK -> Freq.WEEKLY
            Timing.UnitsOfTime.D -> Freq.DAILY
            Timing.UnitsOfTime.H -> Freq.HOURLY
            Timing.UnitsOfTime.MO -> Freq.MONTHLY
            Timing.UnitsOfTime.MIN -> Freq.MINUTELY
            Timing.UnitsOfTime.S -> Freq.SECONDLY
            Timing.UnitsOfTime.A -> Freq.YEARLY
            else -> throw IllegalArgumentException("Unsupported unit of time " + t)
        }
    }
}