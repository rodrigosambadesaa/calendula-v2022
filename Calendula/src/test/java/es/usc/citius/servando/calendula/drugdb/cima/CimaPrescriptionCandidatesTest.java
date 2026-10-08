package es.usc.citius.servando.calendula.drugdb.cima;

import org.junit.Test;
import java.util.List;
import java.util.Arrays;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CimaPrescriptionCandidatesTest {
    @Test
    public void oneRegistrationProducesDistinctNamedCommercialPresentations() {
        String json = "{\"nregistro\":\"51347\",\"nombre\":\"ASPIRINA C\","
                + "\"dosis\":\"400/240 mg/mg\",\"presentaciones\":["
                + "{\"cn\":\"712729\",\"nombre\":\"ASPIRINA C, 10 comprimidos\"},"
                + "{\"cn\":\"651877\",\"nombre\":\"ASPIRINA C, 20 comprimidos\"}]}";
        CimaRestCatalog.MedicineSnapshot snapshot =
                CimaRestCatalog.parseMedicine(json, "51347");
        List<CimaPrescriptionCandidates.Candidate> candidates =
                CimaPrescriptionCandidates.from(snapshot);

        assertEquals(Arrays.asList("712729", "651877"), snapshot.getNationalCodes());
        assertEquals(2, candidates.size());
        assertEquals("51347", candidates.get(0).getRegistrationNumber());
        assertEquals("712729", candidates.get(0).getNationalCode());
        assertEquals("ASPIRINA C, 10 comprimidos", candidates.get(0).getMarketedName());
        assertEquals("651877", candidates.get(1).getNationalCode());
        assertEquals("ASPIRINA C, 20 comprimidos", candidates.get(1).getMarketedName());
        assertEquals("400/240 mg/mg", candidates.get(1).getSourceDoseText());
    }

    @Test
    public void missingPresentationNameFallsBackToProductName() {
        CimaRestCatalog.MedicineSnapshot snapshot =
                CimaRestCatalog.parseMedicine("{\"nregistro\":\"1234\","
                        + "\"nombre\":\"Synthetic product\","
                        + "\"presentaciones\":[{\"cn\":\"123456\"}]}");
        assertEquals("Synthetic product",
                CimaPrescriptionCandidates.from(snapshot).get(0).getMarketedName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void conflictingDuplicateNationalCodesAreRejected() {
        CimaRestCatalog.parseMedicine("{\"nregistro\":\"1234\","
                + "\"nombre\":\"Synthetic product\","
                + "\"presentaciones\":[{\"cn\":\"123456\",\"nombre\":\"Box A\"},"
                + "{\"cn\":\"123456\",\"nombre\":\"Box B\"}]}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void blankPresentationNameIsRejected() {
        CimaRestCatalog.parseMedicine("{\"nregistro\":\"1234\","
                + "\"nombre\":\"Synthetic product\","
                + "\"presentaciones\":[{\"cn\":\"123456\",\"nombre\":\"   \"}]}");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void candidatesCannotBeMutated() {
        CimaRestCatalog.MedicineSnapshot snapshot =
                CimaRestCatalog.parseMedicine("{\"nregistro\":\"1234\","
                        + "\"nombre\":\"Synthetic product\","
                        + "\"presentaciones\":[{\"cn\":\"123456\"}]}");
        CimaPrescriptionCandidates.from(snapshot).clear();
    }

    @Test
    public void emptyPresentationsDoNotInventNationalCodes() {
        CimaRestCatalog.MedicineSnapshot snapshot =
                CimaRestCatalog.parseMedicine(
                        "{\"nregistro\":\"1234\",\"nombre\":\"Synthetic product\"}");
        assertTrue(CimaPrescriptionCandidates.from(snapshot).isEmpty());
    }
}
