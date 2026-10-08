/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.drugdb.cima;

import org.junit.Test;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CimaMedicineSearchTest {

    private static final String SAMPLE =
            "{\"totalFilas\":2,\"pagina\":1,\"tamanoPagina\":25,"
                    + "\"resultados\":[{\"nregistro\":\"51347\","
                    + "\"nombre\":\"ASPIRINA C\",\"comerc\":true,\"receta\":false},"
                    + "{\"nregistro\":\"12345\","
                    + "\"nombre\":\"ACIDO ASCORBICO\"}]}";

    @Test
    public void searchUrlEncodesUserInputRatherThanAllowingQueryInjection() {
        String url = CimaMedicineSearch.url(" Ácido &foo=bar? ", 2);
        assertTrue(url.startsWith("https://cima.aemps.es/cima/rest/medicamentos?nombre="));
        assertTrue(url.contains("%C3%81cido+%26foo%3Dbar%3F"));
        assertTrue(url.endsWith("&pagina=2"));
        assertFalse(url.contains("&foo=bar"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void blankSearchIsRejected() {
        CimaMedicineSearch.url(" ", 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void controlCharactersAreRejected() {
        CimaMedicineSearch.url("a\nmedication", 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void impossiblePageIsRejected() {
        CimaMedicineSearch.url("Aspirina", 0);
    }

    @Test
    public void parsesSafePageWithoutInventingMissingFlags() {
        CimaMedicineSearch.Page page = CimaMedicineSearch.parsePage(SAMPLE, 1);
        assertEquals(1, page.getPageNumber());
        assertEquals(2, page.getTotalResults());
        assertEquals(2, page.getResults().size());
        assertEquals("51347", page.getResults().get(0).getRegistrationNumber());
        assertTrue(page.getResults().get(0).isMarketed());
        assertFalse(page.getResults().get(0).isPrescriptionRequired());
        assertEquals("ACIDO ASCORBICO", page.getResults().get(1).getName());
        assertNull(page.getResults().get(1).isMarketed());
        assertNull(page.getResults().get(1).isPrescriptionRequired());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMismatchedPageToPreventCrossPageMixups() {
        CimaMedicineSearch.parsePage(SAMPLE, 2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsCorruptOrAmbiguousMedicineRegistration() {
        CimaMedicineSearch.parsePage(
                "{\"totalFilas\":1,\"pagina\":1,"
                        + "\"resultados\":[{\"nregistro\":\"abc\","
                        + "\"nombre\":\"Fake\"}]}", 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsConflictingDuplicateMedicineRegistrations() {
        CimaMedicineSearch.parsePage(
                "{\"totalFilas\":2,\"pagina\":1,"
                        + "\"resultados\":[{\"nregistro\":\"123\","
                        + "\"nombre\":\"First\"},"
                        + "{\"nregistro\":\"123\",\"nombre\":\"Second\"}]}", 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnexpectedBooleanTypes() {
        CimaMedicineSearch.parsePage(
                "{\"totalFilas\":1,\"pagina\":1,"
                        + "\"resultados\":[{\"nregistro\":\"123\","
                        + "\"nombre\":\"Medicine\",\"comerc\":\"yes\"}]}", 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsFakeNumericStringPage() {
        CimaMedicineSearch.parsePage(
                "{\"totalFilas\":0,\"pagina\":\"1\",\"resultados\":[]}", 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOversizedTotalCount() {
        CimaMedicineSearch.parsePage(
                "{\"totalFilas\":2147483648,\"pagina\":1,\"resultados\":[]}", 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnpairedHighSurrogateInSearchName() {
        CimaMedicineSearch.url("AB" + (char) 0xD800, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnpairedLowSurrogateInSearchName() {
        CimaMedicineSearch.url("AB" + (char) 0xDC00, 1);
    }

    @Test
    public void acceptsValidSupplementaryUnicodeInSearchName() {
        String emoji = new String(Character.toChars(0x1F600));
        String url = CimaMedicineSearch.url("Med " + emoji, 1);
        assertTrue(url.contains("%F0%9F%98%80"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnpairedSurrogateInJsonMedicineName() {
        String json = "{\\"totalFilas\\":1,\\"pagina\\":1,\\"resultados\\":["
                + "{\\"nregistro\\":\\"123\\",\\"nombre\\":\\"Med"
                + (char) 0xD800 + "\\"}]}";
        CimaMedicineSearch.parsePage(json, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsControlCharactersInJsonMedicineName() {
        // Gson decodes an escaped newline; never normalize it away or
        // accept it as medication metadata.
        String json = "{\\"totalFilas\\":1,\\"pagina\\":1,\\"resultados\\":["
                + "{\\"nregistro\\":\\"123\\",\\"nombre\\":\\"\\\\nMedicine\\"}]}";
        CimaMedicineSearch.parsePage(json, 1);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void pageResultCollectionCannotBeModified() {
        CimaMedicineSearch.parsePage(SAMPLE, 1).getResults().clear();
    }

    @Test
    public void identicalDuplicateRegistrationIsDeduplicated() {
        String json = "{\"totalFilas\":2,\"pagina\":1,\"resultados\":["
                + "{\"nregistro\":\"123\",\"nombre\":\"Medicine\"},"
                + "{\"nregistro\":\"123\",\"nombre\":\"Medicine\"}]}";
        List<CimaMedicineSearch.Result> result =
                CimaMedicineSearch.parsePage(json, 1).getResults();
        assertEquals(1, result.size());
    }
}
