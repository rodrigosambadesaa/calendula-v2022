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

package es.usc.citius.servando.calendula.persistence.typeSerializers;

import com.j256.ormlite.field.FieldType;
import com.j256.ormlite.field.SqlType;
import com.j256.ormlite.field.types.BaseDataType;
import com.j256.ormlite.support.DatabaseResults;

import org.apache.commons.lang3.StringUtils;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DosageListPersister extends BaseDataType {

    public static final String SEPARATOR = "|";
    private static final DosageListPersister singleton = new DosageListPersister();

    public DosageListPersister() {
        super(SqlType.STRING, new Class<?>[]{List.class});
    }

    public static DosageListPersister getSingleton() {
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
        if (sqlArg == null) {
            return null;
        }
        if (!(sqlArg instanceof String)) {
            throw new SQLException("Persisted legacy dosage list is not text");
        }
        String raw = (String) sqlArg;
        List<Double> result = new ArrayList<>();
        if (raw.isEmpty()) {
            return result;
        }
        // Preserve the stored clinical amounts exactly. A malformed legacy
        // dose must not be silently interpreted as zero or dropped.
        String[] entries = raw.split("\\|", -1);
        try {
            for (String entry : entries) {
                if (entry.isEmpty()) {
                    throw new NumberFormatException("Missing legacy dosage entry");
                }
                result.add(Double.parseDouble(entry));
            }
        } catch (NumberFormatException malformed) {
            throw new SQLException("Persisted legacy dosage list is malformed", malformed);
        }
        return result;
    }

    @Override
    public Object javaToSqlArg(FieldType fieldType, Object javaObject) throws SQLException {
        List<Double> dosages = (List<Double>) javaObject;
        return StringUtils.join(dosages, SEPARATOR);
    }
}
