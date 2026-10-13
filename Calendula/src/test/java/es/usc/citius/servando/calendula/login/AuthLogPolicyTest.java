/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.login;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AuthLogPolicyTest {
    @Test
    public void secretBearingProviderFailuresNeverAppearInDebugLogcat() {
        String secret = "SYNTHETIC_ACCESS_TOKEN_DO_NOT_LOG_987";
        RuntimeException exception = new RuntimeException(
                "response?access_token=" + secret + "&code=private&scope=patient/Observation.read");
        int before = ShadowLog.getLogs().size();
        for (AuthLogPolicy.Failure failure : AuthLogPolicy.Failure.values()) {
            assertNotNull(AuthLogPolicy.safeMessage(failure));
            assertFalse(AuthLogPolicy.safeMessage(failure).contains(secret));
            AuthLogPolicy.logFailure("SyntheticAuthLogRedaction", failure, exception);
        }
        java.util.List<ShadowLog.LogItem> logs = ShadowLog.getLogs();
        for (int i = before; i < logs.size(); i++) {
            String message = logs.get(i).msg == null ? "" : logs.get(i).msg;
            assertFalse("Authentication logs must not expose bearer credentials",
                    message.contains(secret));
            assertFalse("Authentication logs must not echo OAuth response params",
                    message.contains("scope=patient/Observation.read"));
            assertFalse("Authentication logs must not contain an auth code",
                    message.contains("code=private"));
        }
        assertTrue("Sensitive cause must not be referenced by safe messages",
                AuthLogPolicy.safeMessage(AuthLogPolicy.Failure.ID_TOKEN)
                        .indexOf("TOKEN_DO_NOT_LOG") < 0);
    }
}
