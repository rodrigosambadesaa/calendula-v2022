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

package es.usc.citius.servando.calendula.persistence.typeSerializers;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.j256.ormlite.field.FieldType;
import com.j256.ormlite.field.SqlType;
import com.j256.ormlite.field.types.BaseDataType;
import com.j256.ormlite.support.DatabaseResults;

import java.lang.reflect.Type;
import java.sql.SQLException;
import java.util.EnumSet;

import es.usc.citius.servando.calendula.persistence.Schedule;

/**
 * Persister for EnumSet<ScheduleState>
 */
public class ScheduleStatePersister extends BaseDataType {

    private static final ScheduleStatePersister singleton = new ScheduleStatePersister();
    private static Type type = new TypeToken<EnumSet<Schedule.ScheduleState>>() {
    }.getType();
    private static Gson gson = new Gson();

    public ScheduleStatePersister() {
        super(SqlType.STRING, new Class<?>[]{EnumSet.class});
    }

    public static ScheduleStatePersister getSingleton() {
        return singleton;
    }

    @Override
    public Object parseDefaultString(FieldType fieldType, String defaultStr) throws SQLException {
        return defaultStr;
    }

    @Override
    public Object resultToSqlArg(FieldType fieldType, DatabaseResults results, int columnPos) throws SQLException {
        return results.getString(columnPos);
    }

    @Override
    public Object sqlArgToJava(FieldType fieldType, Object sqlArg, int columnPos) throws SQLException {
        if (sqlArg != null) {
            return gson.fromJson((String) sqlArg, type);
        } else {
            return EnumSet.noneOf(Schedule.ScheduleState.class);
        }
    }

    @Override
    public Object javaToSqlArg(FieldType fieldType, Object javaObject) throws SQLException {
        return gson.toJson(javaObject);
    }
}
