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

import org.joda.time.LocalTime;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;


public class DailyFixedTime {

    /**
     * A routine id, if {@link #referenceType} is #ROUTINE
     */
    Long reference;
    /**
     * Type of reference
     */
    ReferenceType referenceType;
    /**
     * Time of the occurrence if reference type is ABSOLUTE
     */
    LocalTime time;

    /**
     * Cached time for avoid unnecessary DB queries
     */
    LocalTime cachedTime;

    EventInstance.EventOffset offset;

    public EventInstance.EventOffset offset() {
        return offset;
    }

    public void setOffset(EventInstance.EventOffset offset) {
        this.offset = offset;
    }

    public DailyFixedTime(){}

    public DailyFixedTime(ReferenceType type, LocalTime time) {
        this.referenceType = type;
        this.time = time;
    }

    public DailyFixedTime(Long reference, ReferenceType referenceType) {
        this.reference = reference;
        this.referenceType = referenceType;
    }

    public Long getReference() {
        return reference;
    }

    public void setReference(Long reference) {
        this.reference = reference;
        this.cachedTime = null;
    }

    public ReferenceType getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(ReferenceType referenceType) {
        this.referenceType = referenceType;
        this.cachedTime = null;
    }

    public LocalTime getTime() {

        if(cachedTime !=null) {
            return cachedTime;
        }

        if(ReferenceType.ABSOLUTE.equals(referenceType)) {
            cachedTime = time;
        } else{
            cachedTime = DB.routines().findById(reference).getTime();
        }
        return cachedTime;
    }

    public void setTime(LocalTime time) {
        this.time = time;
        this.cachedTime = time;
    }

    public enum ReferenceType {

        ABSOLUTE("ABSOLUTE"),
        ROUTINE("ROUTINE");

        // let imagination fly...
        // REACHES_HOME
        // GET_OUT_OF_WORK

        private final String name;

        ReferenceType(String s) {
            name = s;
        }

        public String toString() {
            return this.name;
        }
    }


}
