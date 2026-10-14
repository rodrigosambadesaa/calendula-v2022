/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util.prospects;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.persistence.Patient;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
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
        assertTrue(ProspectUtils.canOpenProspect(owner, "https://www.aemps.gob.es/cima/dochtml/p/123/Prospecto_123.html"));
        assertTrue(ProspectUtils.canOpenProspect(owner, "https://www.accessdata.fda.gov/spl/data/42/42.xml"));
        assertFalse(ProspectUtils.canOpenProspect(owner, "javascript:alert(1)"));
        assertFalse(ProspectUtils.canOpenProspect(owner, "data:text/html,payload"));
        assertFalse(ProspectUtils.canOpenProspect(owner, "file:///data/user/0/calendula/secret"));
        assertFalse(ProspectUtils.canOpenProspect(owner, "content://untrusted.provider/leaflet"));
        assertFalse(ProspectUtils.canOpenProspect(owner, "../relative-leaflet.html"));
        assertFalse(ProspectUtils.canOpenProspect(owner, "https://user:password@example.test/leaflet"));
        assertFalse(ProspectUtils.canOpenProspect(owner, "https://example.test/unterminated["));

    }
}
