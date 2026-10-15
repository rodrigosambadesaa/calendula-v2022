/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.login;

import android.app.Activity;
import android.view.Window;
import android.view.WindowManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.util.SecureWindowPolicy;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** No OAuth exchange, credentials or login state is read by these tests. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AuthenticationScreenPrivacyTest {
    @Test
    public void secureByDefaultAlsoProtectsNonCalendulaAuthenticationWindows() {
        Activity activity = mock(Activity.class);
        Window window = mock(Window.class);
        when(activity.getWindow()).thenReturn(window);
        try (MockedStatic<SecureWindowPolicy> policy = mockStatic(SecureWindowPolicy.class)) {
            policy.when(SecureWindowPolicy::isProtected).thenReturn(true);
            AuthenticationScreenPrivacy.protectIfConfigured(activity);
            verify(window).addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    @Test
    public void explicitScreenshotOptOutStillAppliesToLoginScreens() {
        Activity activity = mock(Activity.class);
        try (MockedStatic<SecureWindowPolicy> policy = mockStatic(SecureWindowPolicy.class)) {
            policy.when(SecureWindowPolicy::isProtected).thenReturn(false);
            AuthenticationScreenPrivacy.protectIfConfigured(activity);
            verify(activity, never()).getWindow();
        }
    }
}
