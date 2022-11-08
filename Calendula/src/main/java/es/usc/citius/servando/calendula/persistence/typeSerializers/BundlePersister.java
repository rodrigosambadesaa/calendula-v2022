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

import android.os.Bundle;

import com.j256.ormlite.field.FieldType;
import com.j256.ormlite.field.SqlType;
import com.j256.ormlite.field.types.BaseDataType;
import com.j256.ormlite.support.DatabaseResults;

import org.apache.commons.lang3.StringUtils;

import java.sql.SQLException;
import java.util.Set;

public class BundlePersister extends BaseDataType {

    private static final BundlePersister singleton = new BundlePersister();

    public BundlePersister() {
        super(SqlType.STRING, new Class<?>[]{Bundle.class});
    }

    public static BundlePersister getSingleton() {
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
        String bundleStr = (String) sqlArg;
        Bundle bundle = new Bundle();
        String[] params = bundleStr.split("#");
        for(String param : params){
            String[] kv = StringUtils.split(param, "|");
            bundle.putString(kv[0],kv[1]);
        }
        return bundle;
    }

    @Override
    public Object javaToSqlArg(FieldType fieldType, Object javaObject) throws SQLException {
        Bundle bundle = (Bundle) javaObject;
        String result = "";
        Set<String> keys = bundle.keySet();
        for (String key : keys) {
            result += key + "|" + bundle.get(key) + "#";
        }
        return result;
    }
}
