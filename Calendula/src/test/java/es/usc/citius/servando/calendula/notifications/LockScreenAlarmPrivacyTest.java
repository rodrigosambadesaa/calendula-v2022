/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.notifications;

import android.view.Window;
import android.view.WindowManager;

import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Window flag contract only; never starts a real medication alarm. */
public class LockScreenAlarmPrivacyTest {
    @Test
    public void protectedMedicationAlarmMustExcludeScreenshotsAndRecents() {
        Window window = mock(Window.class);
        LockScreenAlarmActivity.protectAlarmWindow(window, true);
        verify(window).addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }

    @Test
    public void explicitScreenshotOptOutMustBeRespected() {
        Window window = mock(Window.class);
        LockScreenAlarmActivity.protectAlarmWindow(window, false);
        verify(window, never()).addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }

    @Test
    public void missingWindowDoesNotCrashDuringLifecycleTransition() {
        LockScreenAlarmActivity.protectAlarmWindow(null, true);
    }
}
