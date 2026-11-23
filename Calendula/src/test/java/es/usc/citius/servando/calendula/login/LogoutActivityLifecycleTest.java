/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.login;

import android.content.Context;
import android.os.Looper;

import net.openid.appauth.AuthorizationService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class LogoutActivityLifecycleTest {

    @Test
    public void delayedLogoutRequestNeverUsesDisposedAuthenticationService() {
        AuthorizationService authService = mock(AuthorizationService.class);
        try (MockedStatic<AuthorizationServiceHelper> helper =
                     mockStatic(AuthorizationServiceHelper.class)) {
            helper.when(() -> AuthorizationServiceHelper.createAuthorizationService(
                    any(Context.class))).thenReturn(authService);

            ActivityController<LogoutActivity> controller =
                    Robolectric.buildActivity(LogoutActivity.class);
            controller.create().start();
            // Destroy the activity before its one-second timeout fires.
            controller.stop().destroy();
            shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS);

            verify(authService).dispose();
            // A leaked delayed callback would call getCustomTabManager() here
            // after AuthorizationService had been disposed.
            verifyNoMoreInteractions(authService);
        }
    }
}
