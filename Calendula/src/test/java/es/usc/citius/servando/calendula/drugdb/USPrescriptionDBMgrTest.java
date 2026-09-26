/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.drugdb;

import org.junit.Test;

import es.usc.citius.servando.calendula.drugdb.model.persistence.Prescription;
import es.usc.citius.servando.calendula.persistence.Presentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class USPrescriptionDBMgrTest {

    private final USPrescriptionDBMgr manager = new USPrescriptionDBMgr();

    @Test
    public void prospectUrlUsesHttpsAndPreservesSplPath() {
        Prescription prescription = new Prescription();
        prescription.setPID("abc-123");

        String url = manager.getProspectURL(prescription);

        assertTrue(url.startsWith("https://"));
        assertEquals(
                "https://www.accessdata.fda.gov/spl/data/abc-123/abc-123.xml",
                url);
    }

    @Test
    public void liquidMedicationIsInferredAsSyrup() {
        assertEquals(
                Presentation.SYRUP,
                manager.expectedPresentation("Acme oral liquid", "solution"));
    }

    @Test
    public void unrelatedUnknownMedicationIsNotInferredAsSyrup() {
        assertNull(manager.expectedPresentation("Acme implant", "extended release"));
    }

    @Test
    public void presentationInferenceIsNullSafe() {
        assertNull(manager.expectedPresentation((String) null, null));
    }

    @Test
    public void presentationInferenceUsesLocaleIndependentLowercase() {
        assertEquals(
                Presentation.PILLS,
                manager.expectedPresentation("TABLET", null));
    }

    @Test
    public void csvParserPreservesEmptyTrailingContent() {
        Prescription prescription = manager.fromCsv("123|Example medicine|", "\\|");

        assertEquals("123", prescription.getPID());
        assertEquals("Example medicine", prescription.getName());
        assertEquals("", prescription.getContent());
    }

    @Test
    public void csvParserSkipsRowsWithoutMedicineName() {
        assertNull(manager.fromCsv("123||content", "\\|"));
    }
}
