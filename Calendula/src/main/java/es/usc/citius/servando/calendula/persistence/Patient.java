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

package es.usc.citius.servando.calendula.persistence;

import android.graphics.Color;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import es.usc.citius.servando.calendula.util.AvatarMgr;

/**
 * Models an user
 */
@DatabaseTable(tableName = "Patients")
public class Patient {

    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_CODE = "Code";
    public static final String COLUMN_NAME = "Name";
    public static final String COLUMN_DEFAULT = "Default";
    public static final String COLUMN_DATA_RETRIEVED = "DataRetrieved";
    public static final String COLUMN_AVATAR = "Avatar";
    public static final String COLUMN_COLOR = "Color";


    @DatabaseField(columnName = COLUMN_ID, generatedId = true)
    private Long id;

    @DatabaseField(columnName = COLUMN_CODE, unique = true)
    private String code;

    @DatabaseField(columnName = COLUMN_NAME)
    private String name;

    @DatabaseField(columnName = COLUMN_DEFAULT)
    private boolean isDefault = false;

    /**
     * Indicates whether the user's data has been recovered yet.
     * This means if there has been a call to the data service that has
     * returned a name for the patient.
     */
    @DatabaseField(columnName = COLUMN_DATA_RETRIEVED)
    private boolean dataRetrieved = false;

    @DatabaseField(columnName = COLUMN_AVATAR)
    private String avatar = AvatarMgr.DEFAULT_AVATAR;

    @DatabaseField(columnName = COLUMN_COLOR)
    private int color = Color.parseColor("#2c3e50"); // material blue 700 1976d2

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public int getColor() {
        return color;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public boolean isDataRetrieved() {
        return dataRetrieved;
    }

    public void setDataRetrieved(boolean dataRetrieved) {
        this.dataRetrieved = dataRetrieved;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Patient patient = (Patient) o;

        if (isDefault != patient.isDefault) return false;
        if (dataRetrieved != patient.dataRetrieved) return false;
        if (color != patient.color) return false;
        if (!id.equals(patient.id)) return false;
        if (code != null ? !code.equals(patient.code) : patient.code != null) return false;
        if (name != null ? !name.equals(patient.name) : patient.name != null) return false;
        return avatar != null ? avatar.equals(patient.avatar) : patient.avatar == null;

    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + (code != null ? code.hashCode() : 0);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (isDefault ? 1 : 0);
        result = 31 * result + (dataRetrieved ? 1 : 0);
        result = 31 * result + (avatar != null ? avatar.hashCode() : 0);
        result = 31 * result + color;
        return result;
    }

    @Override
    public String toString() {
        return "Patient{" +
                "id=" + id +
                ", code='" + code + '\'' +
                ", name='" + name + '\'' +
                ", isDefault=" + isDefault +
                ", dataRetrieved=" + dataRetrieved +
                ", avatar='" + avatar + '\'' +
                ", color=" + color +
                '}';
    }
}
