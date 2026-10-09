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

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import java.util.Objects;

import es.usc.citius.servando.calendula.allergies.AllergenType;
import es.usc.citius.servando.calendula.allergies.AllergenVO;

/**
 * Models an user
 */
@DatabaseTable(tableName = "Allergens")
public class PatientAllergen {

    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_ALLERGEN_TYPE = "Type";
    public static final String COLUMN_IDENTIFIER = "Identifier";
    public static final String COLUMN_NAME = "Name";
    public static final String COLUMN_PATIENT = "Patient";
    public static final String COLUMN_GROUP = "Group";


    @DatabaseField(columnName = COLUMN_ID, generatedId = true)
    private Long id;

    @DatabaseField(columnName = COLUMN_NAME)
    private String name;

    @DatabaseField(columnName = COLUMN_ALLERGEN_TYPE, uniqueCombo = true)
    private AllergenType type;

    @DatabaseField(columnName = COLUMN_IDENTIFIER, uniqueCombo = true)
    private String identifier;

    @DatabaseField(columnName = COLUMN_PATIENT, foreign = true, foreignAutoRefresh = true, uniqueCombo = true)
    private Patient patient;

    @DatabaseField(columnName = COLUMN_GROUP)
    private String group;

    public PatientAllergen() {
    }

    public PatientAllergen(String name, AllergenType type, String identifier, Patient patient) {
        this.name = name;
        this.type = type;
        this.identifier = identifier;
        this.patient = patient;
    }


    public PatientAllergen(AllergenVO allergenVO, Patient patient) {
        this.name = allergenVO.getName();
        this.type = allergenVO.getType();
        this.identifier = allergenVO.getIdentifier();
        this.patient = patient;
    }

    public PatientAllergen(AllergenVO allergenVO, Patient patient, String group) {
        this(allergenVO, patient);
        this.group = group;
    }


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

    public AllergenType getType() {
        return type;
    }

    public void setType(AllergenType type) {
        this.type = type;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    /** Allergen names and clinical identifiers must not leak into logs. */
    @Override
    public String toString() {
        return "PatientAllergen{" +
                "type=" + type +
                ", hasPatient=" + (patient != null) +
                ", hasDetails=" + (identifier != null || name != null) +
                '}';
    }

    /**
     * A patient's allergy identity is (patient, type, external identifier).
     * Compare the identifier by String VALUE, not JVM object identity:
     * identifiers loaded from SQLite/network can be distinct String objects.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PatientAllergen allergen = (PatientAllergen) o;
        return type == allergen.type
                && Objects.equals(identifier, allergen.identifier)
                && samePatientRecord(patient, allergen.patient);
    }

    private static boolean samePatientRecord(Patient a, Patient b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        // ORMLite may hydrate the same patient as different Java objects.
        // The database PK is stable even if name/avatar were edited.
        Long aId = a.getId();
        return aId != null && aId.equals(b.getId());
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(type, identifier);
        // Unsaved patients have no PK: retain instance identity without
        // calling the legacy Patient.hashCode(), which assumes id != null.
        int patientHash = patient == null ? 0
                : patient.getId() != null ? patient.getId().hashCode()
                : System.identityHashCode(patient);
        return 31 * result + patientHash;
    }
}
