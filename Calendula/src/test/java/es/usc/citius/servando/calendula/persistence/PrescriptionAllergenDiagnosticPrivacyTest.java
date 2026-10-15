package es.usc.citius.servando.calendula.persistence;

import org.junit.Test;
import es.usc.citius.servando.calendula.drugdb.model.persistence.Prescription;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PrescriptionAllergenDiagnosticPrivacyTest {
    @Test
    public void allergenNameIdAndPrescriptionAreNotLoggedViaToString() {
        PrescriptionAllergen allergen = new PrescriptionAllergen();
        allergen.setName("Synthetic confidential allergen");
        allergen.setRealId(987654321);
        allergen.setPrescription(new Prescription());
        String diagnostic = allergen.toString();
        assertFalse(diagnostic.contains("Synthetic confidential allergen"));
        assertFalse(diagnostic.contains("987654321"));
        assertFalse(diagnostic.contains("prescription="));
        assertTrue(diagnostic.contains("hasPrescription=true"));
        assertTrue(diagnostic.contains("hasName=true"));
    }

    @Test
    public void emptyAllergenCanBeLoggedWithoutAccessingAnyClinicalRecord() {
        PrescriptionAllergen allergen = new PrescriptionAllergen();
        assertTrue(allergen.toString().contains("hasPrescription=false"));
    }
}
