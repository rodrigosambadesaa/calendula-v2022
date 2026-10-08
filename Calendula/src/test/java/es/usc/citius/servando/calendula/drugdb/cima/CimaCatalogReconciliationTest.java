/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.drugdb.cima;

import es.usc.citius.servando.calendula.drugdb.model.persistence.Prescription;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class CimaCatalogReconciliationTest {

    private static List<CimaPrescriptionCandidates.Candidate> incoming(
            String registration, String cn, String label, String dose) {
        String json = "{\"nregistro\":\"" + registration + "\","
                + "\"nombre\":\"Synthetic medicine\","
                + "\"dosis\":\"" + dose + "\","
                + "\"presentaciones\":[{\"cn\":\"" + cn + "\","
                + "\"nombre\":\"" + label + "\"}]}";
        return CimaPrescriptionCandidates.from(
                CimaRestCatalog.parseMedicine(json, registration));
    }

    private static Prescription old(String cn, String registration,
                                    String name, String dose) {
        Prescription item = new Prescription();
        item.setCode(cn);
        item.setPID(registration);
        item.setName(name);
        item.setDose(dose);
        return item;
    }

    @Test
    public void newCatalogPresentationDoesNotChangeLegacyRecords() {
        Prescription original = old("123456", "111", "Saved package", "1 mg");
        List<Prescription> historical = new ArrayList<>();
        historical.add(original);
        CimaCatalogReconciliation.Plan result = CimaCatalogReconciliation.preview(
                incoming("222", "654321", "New package", "2 mg"), historical);

        assertEquals(2, result.getEntries().size());
        assertEquals(CimaCatalogReconciliation.Status.NEW_PRESENTATION,
                result.getEntries().get(0).getStatus());
        assertEquals("654321", result.getEntries().get(0).getNationalCode());
        assertEquals(CimaCatalogReconciliation.Status.HISTORICAL_ONLY,
                result.getEntries().get(1).getStatus());
        assertEquals("123456", result.getEntries().get(1).getNationalCode());
        assertSame(original, historical.get(0));
        assertEquals("Saved package", original.getName());
    }

    @Test
    public void matchingIdentifierAndMetadataDoNotRequireReassignment() {
        CimaCatalogReconciliation.Plan result = CimaCatalogReconciliation.preview(
                incoming("111", "123456", "Matching package", "1 mg"),
                Collections.singletonList(old("123456", "111", "Matching package", "1 mg")));
        assertEquals(CimaCatalogReconciliation.Status.UNCHANGED_METADATA,
                result.getEntries().get(0).getStatus());
    }

    @Test
    public void conflictingRegistrationIsFlaggedForReviewWithoutMutation() {
        Prescription original = old("123456", "legacy-identifier",
                "Saved patient-linked product", "1 mg");
        CimaCatalogReconciliation.Plan result = CimaCatalogReconciliation.preview(
                incoming("222", "123456", "Changed product", "1 mg"),
                Collections.singletonList(original));

        assertEquals(CimaCatalogReconciliation.Status.REQUIRES_REVIEW,
                result.getEntries().get(0).getStatus());
        assertEquals("legacy-identifier", original.getPID());
        assertEquals("Saved patient-linked product", original.getName());
    }

    @Test
    public void missingNationalCodesArePreservedWithoutManufacturingReferences() {
        CimaCatalogReconciliation.Plan result = CimaCatalogReconciliation.preview(
                Collections.emptyList(), Arrays.asList(
                        old(null, "111", "Historical medicine", null),
                        old("  ", "222", "Another medicine", null)));
        assertTrue(result.getEntries().isEmpty());
        assertEquals(2, result.getPreservedLegacyRowsWithoutCode());
    }

    @Test(expected = IllegalArgumentException.class)
    public void duplicateExistingNationalCodeRejectsUnsafeReconciliation() {
        CimaCatalogReconciliation.preview(Collections.emptyList(), Arrays.asList(
                old("123456", "111", "Product A", "1 mg"),
                old("123456", "222", "Product B", "1 mg")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void sameNationalCodeCannotBeAssignedToDifferentCimaRegistrations() {
        List<CimaPrescriptionCandidates.Candidate> items = new ArrayList<>();
        items.addAll(incoming("111", "123456", "Product A", "1 mg"));
        items.addAll(incoming("222", "123456", "Product A", "1 mg"));
        CimaCatalogReconciliation.preview(items, Collections.emptyList());
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullHistoricalRowRejectsReconciliation() {
        CimaCatalogReconciliation.preview(Collections.emptyList(),
                Collections.singletonList(null));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void reconciliationPlanIsImmutable() {
        CimaCatalogReconciliation.preview(incoming("111", "123456", "A", "1 mg"),
                Collections.emptyList()).getEntries().clear();
    }

    @Test
    public void missingDoseIsPreservedAndComparedWithoutMedicalInference() {
        Prescription existing = old("123456", "111", "Package", null);
        CimaRestCatalog.MedicineSnapshot snapshot =
                CimaRestCatalog.parseMedicine("{\"nregistro\":\"111\","
                        + "\"nombre\":\"Synthetic medicine\","
                        + "\"presentaciones\":[{\"cn\":\"123456\","
                        + "\"nombre\":\"Package\"}]}");
        CimaCatalogReconciliation.Plan plan = CimaCatalogReconciliation.preview(
                CimaPrescriptionCandidates.from(snapshot),
                Collections.singletonList(existing));
        assertNull(existing.getDose());
        assertEquals(CimaCatalogReconciliation.Status.UNCHANGED_METADATA,
                plan.getEntries().get(0).getStatus());
    }
}
