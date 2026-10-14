/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.healthcareprovider.jobs;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.events.ActiveMedUpdateEvent.Status;
import es.usc.citius.servando.calendula.healthcareprovider.fhir.ResponseVO;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class MedicationUpdateUnauthorizedResponseTest {
    @Test
    public void unauthorizedResponseWithoutFhirHeaderStillRequiresAuthentication() {
        ResponseVO response = new ResponseVO();
        response.setCode(java.net.HttpURLConnection.HTTP_UNAUTHORIZED);
        // The server has supplied no FHIR MessageHeader or clinical metadata.
        assertEquals(Status.ERROR_AUTHORIZATION,
                UpdateMedicationHelper.onAuthorizationError(response, null));
    }
}
