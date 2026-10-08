/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.drugdb.cima;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CimaBatchConsistencyTest {

    private CimaRestCatalog.MedicineSnapshot medicine(
            String registration, String code, String packageName, String dose) {
        String json = "{\"nregistro\":\"" + registration + "\","
                + "\"nombre\":\"Synthetic medicine\",\"dosis\":\"" + dose + "\","
                + "\"presentaciones\":[{\"cn\":\"" + code + "\","
                + "\"nombre\":\"" + packageName + "\"}]}";
        return CimaRestCatalog.parseMedicine(json, registration);
    }

    @Test
    public void distinctNationalCodesRemainSeparateAndOrdered() {
        List<CimaPrescriptionCandidates.Candidate> candidates =
                CimaPrescriptionCandidates.compileBatch(Arrays.asList(
                        medicine("111", "123456", "Package A", "1 mg"),
                        medicine("222", "654321", "Package B", "2 mg")));
        assertEquals(2, candidates.size());
        assertEquals("123456", candidates.get(0).getNationalCode());
        assertEquals("111", candidates.get(0).getRegistrationNumber());
        assertEquals("Package B", candidates.get(1).getMarketedName());
    }

    @Test
    public void identicalDuplicatesAreIdempotent() {
        CimaRestCatalog.MedicineSnapshot snapshot =
                medicine("111", "123456", "Package A", "1 mg");
        assertEquals(1, CimaPrescriptionCandidates.compileBatch(
                Arrays.asList(snapshot, snapshot)).size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void sameCnCannotSilentlyBelongToTwoRegistrations() {
        CimaPrescriptionCandidates.compileBatch(Arrays.asList(
                medicine("111", "123456", "Package A", "1 mg"),
                medicine("222", "123456", "Package A", "1 mg")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void sameCnCannotSilentlyChangePackDescription() {
        CimaPrescriptionCandidates.compileBatch(Arrays.asList(
                medicine("111", "123456", "Package A", "1 mg"),
                medicine("111", "123456", "Package B", "1 mg")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void sameCnCannotSilentlyDisagreeOnSourceDose() {
        CimaPrescriptionCandidates.compileBatch(Arrays.asList(
                medicine("111", "123456", "Package A", "1 mg"),
                medicine("111", "123456", "Package A", "2 mg")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullBatchIsRejected() {
        CimaPrescriptionCandidates.compileBatch(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullMedicineInBatchIsRejected() {
        CimaPrescriptionCandidates.compileBatch(
                Arrays.asList(medicine("111", "123456", "Package A", "1 mg"), null));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void validatedBatchResultIsImmutable() {
        CimaPrescriptionCandidates.compileBatch(Collections.singletonList(
                medicine("111", "123456", "Package A", "1 mg"))).clear();
    }

    @Test
    public void emptyBatchDoesNotInventMedicinePackages() {
        assertTrue(CimaPrescriptionCandidates.compileBatch(
                Collections.emptyList()).isEmpty());
    }
}
