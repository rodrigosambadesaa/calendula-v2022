/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.drugdb;

import org.junit.Test;

import es.usc.citius.servando.calendula.drugdb.model.persistence.Prescription;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class USPrescriptionDBMgrTest {

    @Test
    public void prospectUrlUsesHttpsAndPreservesSplPath() {
        Prescription prescription = new Prescription();
        prescription.setPID("abc-123");

        String url = new USPrescriptionDBMgr().getProspectURL(prescription);

        assertTrue(url.startsWith("https://"));
        assertEquals(
                "https://www.accessdata.fda.gov/spl/data/abc-123/abc-123.xml",
                url);
    }
}
