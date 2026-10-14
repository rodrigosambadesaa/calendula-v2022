/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util.prospects;

import org.junit.Test;

import es.usc.citius.servando.calendula.persistence.Patient;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProspectUtilsValidationTest {
    @Test
    public void aMissingOrUnpersistedOwnerMustNotLaunchAClinicalLeaflet() {
        assertFalse(ProspectUtils.canOpenProspect(null, "https://example.test/leaflet"));
        Patient missing = new Patient();
        assertFalse(ProspectUtils.canOpenProspect(missing, "https://example.test/leaflet"));
        missing.setId(0L);
        assertFalse(ProspectUtils.canOpenProspect(missing, "https://example.test/leaflet"));
        missing.setId(-1L);
        assertFalse(ProspectUtils.canOpenProspect(missing, "https://example.test/leaflet"));
    }

    @Test
    public void providerMustSupplyAnActualLeafletLocation() {
        Patient owner = new Patient();
        owner.setId(123L);
        assertFalse(ProspectUtils.canOpenProspect(owner, null));
        assertFalse(ProspectUtils.canOpenProspect(owner, ""));
        assertFalse(ProspectUtils.canOpenProspect(owner, "  "));
        assertTrue(ProspectUtils.canOpenProspect(owner, "https://example.test/leaflet"));
    }
}
