/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.healthcareprovider.jobs;

import net.openid.appauth.AuthState;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.assertFalse;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class UpdateMedicationAuthGuardTest {
    @Test
    public void missingOAuthStateCannotTriggerMedicationNetworkSync() {
        assertFalse(UpdateMedicationHelper.hasUsableAccessToken(null));
        assertFalse(UpdateMedicationHelper.hasUsableAccessToken(new AuthState()));
    }
}
