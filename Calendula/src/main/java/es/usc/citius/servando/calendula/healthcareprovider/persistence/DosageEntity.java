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

package es.usc.citius.servando.calendula.healthcareprovider.persistence;

import androidx.annotation.NonNull;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.field.ForeignCollectionField;
import com.j256.ormlite.table.DatabaseTable;

import java.util.Collection;

/**
 * Represents a medication dosage
 */
@DatabaseTable(tableName = "ActiveMedDosage")
public class DosageEntity {

    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_ACTIVE_MED = "ActiveMed";
    public static final String COLUMN_DOSAGE_TYPE = "DosageType";
    public static final String COLUMN_ENTRIES = "DosageEntries";

    /**
     * Auto generated ID
     */
    @DatabaseField(columnName = COLUMN_ID, generatedId = true)
    private Long id;

    /**
     * Dosage type, one of {@link DosageType}
     */
    @NonNull
    @DatabaseField(columnName = COLUMN_DOSAGE_TYPE)
    private DosageType type;

    @DatabaseField(foreign = true, foreignAutoRefresh = true, columnName = COLUMN_ACTIVE_MED)
    private ActiveMedEntity activeMed;

    /**
     * List of Dosage entries:
     * - When type is set to AS_NEEDED, this list should be empty
     * - When type is set to GENERAL, this list should have exactly on item
     * - When type is set to DETAILED, this list should have at one or more items
     */
    @ForeignCollectionField(columnName = COLUMN_ENTRIES)
    private Collection<DosageEntryEntity> entries;

    public DosageEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Dosage type, one of {@link DosageType}
     */
    @NonNull
    public DosageType getType() {
        return type;
    }

    public void setType(@NonNull DosageType type) {
        this.type = type;
    }

    public Collection<DosageEntryEntity> getEntries() {
        return entries;
    }

    public void setEntries(Collection<DosageEntryEntity> entries) {
        this.entries = entries;
    }

    public ActiveMedEntity getActiveMed() {
        return activeMed;
    }

    public void setActiveMed(ActiveMedEntity activeMed) {
        this.activeMed = activeMed;
    }

    /**
     * Do not expose prescription names, codes, identifiers or doses in logs.
     * A detached dosage entity must also be printable before it is linked to
     * a healthcare-provider medication (activeMed may be null).
     */
    @Override
    public String toString() {
        return "DosageEntity{" +
                "hasType=" + (type != null) +
                ", hasActiveMed=" + (activeMed != null) +
                ", hasEntries=" + (entries != null) +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DosageEntity that = (DosageEntity) o;

        if (type != that.type) {
            return false;
        }
        if (entries == null || that.entries == null){
            return false;
        }
        if(entries.size() != that.entries.size()){
            return false;
        }
        if (!entries.containsAll(that.entries)){
            return false;
        }
        if (!that.entries.containsAll(entries)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        // Legacy/partially imported provider rows may have no dosage type.
        // Hashing them must not crash model collections or audit/recovery code.
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (entries != null ? entries.hashCode() : 0);
        return result;
    }
}
