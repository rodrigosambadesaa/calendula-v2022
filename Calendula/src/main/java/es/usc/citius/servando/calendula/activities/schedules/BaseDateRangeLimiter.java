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

package es.usc.citius.servando.calendula.activities.schedules;

import android.os.Parcel;
import android.os.Parcelable;

import com.wdullaer.materialdatetimepicker.date.DateRangeLimiter;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import java.util.Calendar;
import java.util.Locale;

/**
 * Offers a default implementation for DateRangeLimiter
 */
public class BaseDateRangeLimiter implements DateRangeLimiter {


    public final Parcelable.Creator<BaseDateRangeLimiter> CREATOR = new Parcelable.Creator<BaseDateRangeLimiter>() {
        public BaseDateRangeLimiter createFromParcel(Parcel in) {
            return new BaseDateRangeLimiter(in);
        }

        public BaseDateRangeLimiter[] newArray(int size) {
            return new BaseDateRangeLimiter[size];
        }
    };


    public BaseDateRangeLimiter() {
    }

    public BaseDateRangeLimiter(Parcel in) {

    }

    @Override
    public int getMinYear() {
        return new LocalDate().getYear();
    }

    @Override
    public int getMaxYear() {
        return getMinYear() + 10;
    }

    @Override
    public Calendar getStartDate() {
        return DateTime.now().withDayOfMonth(1).toCalendar(Locale.getDefault());
    }

    @Override
    public Calendar getEndDate() {
        return DateTime.now().withYear(getMaxYear()).toCalendar(Locale.getDefault());
    }

    @Override
    public boolean isOutOfRange(int year, int month, int day) {
        return !isValid(new LocalDate(year, month + 1, day));
    }

    @Override
    public Calendar setToNearestDate(Calendar day) {
        return day;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {

    }

    // To be override by subclasses
    boolean isValid(LocalDate d) {
        return true;
    }

}