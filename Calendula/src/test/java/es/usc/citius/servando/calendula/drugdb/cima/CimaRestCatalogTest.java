/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.drugdb.cima;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CimaRestCatalogTest {

    @Test
    public void detailUrlUsesTrustedOfficialHttpsOrigin() {
        assertEquals(
                "https://cima.aemps.es/cima/rest/medicamento?nregistro=51347",
                CimaRestCatalog.detailUrl("51347"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void detailUrlRejectsInjectedQueryParameters() {
        CimaRestCatalog.detailUrl("51347&other=1");
    }

    @Test
    public void parsesPublicCimaJsonShapeWithoutPersistingIt() {
        String sample = "{\"nregistro\":\"51347\",\"nombre\":\"Synthetic medicine\","
                + "\"dosis\":\"20 mg\",\"presentaciones\":["
                + "{\"cn\":\"712729\"},{\"cn\":\"651877\"},{\"cn\":\"712729\"}]}";

        CimaRestCatalog.MedicineSnapshot record = CimaRestCatalog.parseMedicine(sample);

        assertEquals("51347", record.getRegistrationNumber());
        assertEquals("Synthetic medicine", record.getName());
        assertEquals("20 mg", record.getDose());
        assertEquals(Arrays.asList("712729", "651877"), record.getNationalCodes());
    }

    @Test
    public void optionalPresentationAndDoseFieldsCanBeAbsent() {
        CimaRestCatalog.MedicineSnapshot record =
                CimaRestCatalog.parseMedicine(
                        "{\"nregistro\":\"12345\",\"nombre\":\"Synthetic catalog item\"}");
        assertNull(record.getDose());
        assertEquals(0, record.getNationalCodes().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void cannotUseAHtmlOrArrayResponseAsMedicationJson() {
        CimaRestCatalog.parseMedicine("[{\"nregistro\":\"51347\"}]");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMissingRegistrationNumber() {
        CimaRestCatalog.parseMedicine("{\"nombre\":\"Item\"}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidNationalCodeInsteadOfSilentlyDroppingIt() {
        CimaRestCatalog.parseMedicine(
                "{\"nregistro\":\"51347\",\"nombre\":\"Item\","
                        + "\"presentaciones\":[{\"cn\":\"123&admin=true\"}]}");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void snapshotNationalCodesCannotBeMutated() {
        CimaRestCatalog.MedicineSnapshot snapshot = CimaRestCatalog.parseMedicine(
                "{\"nregistro\":\"12345\",\"nombre\":\"Item\","
                        + "\"presentaciones\":[{\"cn\":\"123456\"}]}");
        snapshot.getNationalCodes().add("222222");
    }
}
