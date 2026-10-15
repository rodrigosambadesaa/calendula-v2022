package es.usc.citius.servando.calendula.healthcareprovider.model;

import org.joda.time.LocalTime;
import org.junit.Test;

import es.usc.citius.servando.calendula.healthcareprovider.persistence.DosageEntryEntity;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** In-memory provider record only; no database reads, HTTP or clinical state. */
public class DosageDiagnosticsPrivacyTest {
    @Test
    public void prescriptionTextAndInstructionsCannotLeakThroughModelToString() {
        DosageEntryEntity backing = new DosageEntryEntity();
        backing.setText("private synthetic medicine dosage");
        backing.setPatientInstruction("synthetic private patient instruction");
        backing.setAt(new LocalTime(7, 45));

        String entityDiagnostic = backing.toString();
        String viewModelDiagnostic = new DosageEntryVO(backing).toString();

        for (String diagnostic : new String[]{entityDiagnostic, viewModelDiagnostic}) {
            assertFalse(diagnostic.contains("private synthetic"));
            assertFalse(diagnostic.contains("synthetic private"));
            assertFalse(diagnostic.contains("07:45"));
            assertFalse(diagnostic.contains("patient instruction"));
            assertFalse(diagnostic.contains("medicine dosage"));
        }
        assertTrue(entityDiagnostic.contains("hasText=true"));
        assertTrue(entityDiagnostic.contains("hasPatientInstruction=true"));
        assertTrue(viewModelDiagnostic.contains("hasBackingEntity=true"));
    }

    @Test
    public void emptyDosageStillHasSafeDiagnosticForm() {
        DosageEntryEntity empty = new DosageEntryEntity();
        assertTrue(empty.toString().contains("hasText=false"));
        assertTrue(empty.toString().contains("hasPatientInstruction=false"));
    }
}
