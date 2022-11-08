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

/**
 * Schedule custom repeat view mode
 */
enum ViewMode {

    CUSTOM_SOME_DAYS(0),
    CUSTOM_DAILY(1),
    CUSTOM_WEEKLY(2),
    CUSTOM_REST(3),
    EVERY_DAY(4);

    public final int code;

    ViewMode(int name) {
        this.code = name;
    }

    static ViewMode from(int code) {
        switch (code) {
            case 0:
                return CUSTOM_SOME_DAYS;
            case 1:
                return CUSTOM_DAILY;
            case 2:
                return CUSTOM_WEEKLY;
            case 3:
                return CUSTOM_REST;
            default:
                return EVERY_DAY;
        }
    }

}
