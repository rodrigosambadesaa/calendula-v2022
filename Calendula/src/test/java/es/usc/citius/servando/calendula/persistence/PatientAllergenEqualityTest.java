/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.persistence;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import es.usc.citius.servando.calendula.allergies.AllergenType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/** Synthetic patient/allergy identifiers; no medical data or DB mutation. */
public class PatientAllergenEqualityTest {

    @Test
    public void identicalStringValuesFromDifferentObjectsAreSameAllergen() {
        Patient patient = new Patient();
        patient.setId(9000005541L);
        PatientAllergen a = new PatientAllergen("Synthetic drug A",
                AllergenType.ACTIVE_INGREDIENT, new String("synthetic-ingredient-001"), patient);
        PatientAllergen b = new PatientAllergen("Name revised",
                AllergenType.ACTIVE_INGREDIENT, new String("synthetic-ingredient-001"), patient);
        assertFalse("Fixtures must have separate String instances",
                a.getIdentifier() == b.getIdentifier());
        assertEquals("Logical identifier equality must not depend on String internals", a, b);
        assertEquals("Equal patient allergens require an identical hash code",
                a.hashCode(), b.hashCode());

        Set<PatientAllergen> unique = new HashSet<>();
        assertTrue(unique.add(a));
        assertFalse("A second equivalent row must not create a duplicate", unique.add(b));
        assertEquals(1, unique.size());
    }

    @Test
    public void differentAllergenTypeOrPatientMustRemainDistinct() {
        Patient a = new Patient();
        a.setId(9000005542L);
        Patient b = new Patient();
        b.setId(9000005543L);
        PatientAllergen first = new PatientAllergen("Fixture",
                AllergenType.ACTIVE_INGREDIENT, "synthetic-A", a);
        assertNotEquals(first, new PatientAllergen("Fixture",
                AllergenType.EXCIPIENT, "synthetic-A", a));
        assertNotEquals(first, new PatientAllergen("Fixture",
                AllergenType.ACTIVE_INGREDIENT, "synthetic-A", b));
        assertNotEquals(first, new PatientAllergen("Fixture",
                AllergenType.ACTIVE_INGREDIENT, "synthetic-B", a));
    }

    @Test
    public void nullableLegacyIdentifiersHaveSafeEqualityAndHashCode() {
        Patient a = new Patient();
        a.setId(9000005544L);
        PatientAllergen missingA = new PatientAllergen("Unknown",
                AllergenType.ACTIVE_INGREDIENT, null, a);
        PatientAllergen missingB = new PatientAllergen("Unknown",
                AllergenType.ACTIVE_INGREDIENT, null, a);
        assertEquals(missingA, missingB);
        assertEquals(missingA.hashCode(), missingB.hashCode());
        assertNotEquals(missingA, new PatientAllergen("Known",
                AllergenType.ACTIVE_INGREDIENT, "known-key", a));
        assertNotEquals(missingA, null);
    }
}
