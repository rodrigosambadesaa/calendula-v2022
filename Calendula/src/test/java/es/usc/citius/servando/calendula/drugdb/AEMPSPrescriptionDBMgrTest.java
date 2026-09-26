/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.drugdb;

import org.junit.Test;

import java.util.Locale;

import es.usc.citius.servando.calendula.drugdb.model.persistence.Prescription;
import es.usc.citius.servando.calendula.persistence.Presentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class AEMPSPrescriptionDBMgrTest {

    private final AEMPSPrescriptionDBMgr manager = new AEMPSPrescriptionDBMgr();

    @Test
    public void liquidOralSuspensionMapsToSyrup() {
        assertEquals(
                Presentation.SYRUP,
                manager.expectedPresentation("suspensión oral", "lista para administrar"));
    }

    @Test
    public void oralSuspensionPowderMapsToPowder() {
        assertEquals(
                Presentation.POWDER,
                manager.expectedPresentation("suspensión oral", "polvo para reconstituir"));
    }

    @Test
    public void oralSuspensionGranulesMapToPowder() {
        assertEquals(
                Presentation.POWDER,
                manager.expectedPresentation("suspension oral", "granulado para reconstituir"));
    }

    @Test
    public void sachetsKeepExistingEffervescentClassification() {
        assertEquals(
                Presentation.EFFERVESCENT,
                manager.expectedPresentation("suspensión oral en sobres", "granulado"));
    }

    @Test
    public void nullNameAndContentReturnUnknown() {
        assertEquals(Presentation.UNKNOWN, manager.expectedPresentation(null, null));
    }

    @Test
    public void missingPresentationFormFallsBackToTextInference() {
        Prescription prescription = new Prescription();
        prescription.setPresentationForm(null);
        prescription.setName("Medicamento en comprimidos");
        prescription.setContent("");

        assertEquals(Presentation.PILLS, manager.expectedPresentation(prescription));
    }

    @Test
    public void inferenceDoesNotDependOnDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            assertEquals(
                    Presentation.INJECTIONS,
                    manager.expectedPresentation("INYECTABLE", null));
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    public void shortNameHandlesMissingDose() {
        Prescription prescription = new Prescription();
        prescription.setName("Medicamento de prueba");
        prescription.setDose(null);

        assertEquals("Medicamento de prueba", manager.shortName(prescription));
    }

    @Test
    public void shortNameHandlesMissingPrescription() {
        assertNull(manager.shortName(null));
    }
}
