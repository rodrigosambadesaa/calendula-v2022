/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.drugdb;

import org.junit.Test;

import es.usc.citius.servando.calendula.persistence.Presentation;

import static org.junit.Assert.assertEquals;

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
}
