package es.usc.citius.servando.calendula.notifications;

import android.view.Window;
import android.view.WindowManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class LockScreenAlarmWindowPrivacyTest {
    @Test
    public void clinicalAlarmScreenBlocksScreenshotsWhenPrivacyEnabled() {
        Window window = mock(Window.class);
        LockScreenAlarmActivity.applyScreenshotProtection(window, true);
        verify(window).addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }

    @Test
    public void userOptOutDoesNotAddScreenshotProtectionFlag() {
        Window window = mock(Window.class);
        LockScreenAlarmActivity.applyScreenshotProtection(window, false);
        verify(window, never()).addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }

    @Test
    public void missingWindowDoesNotCrashAlarmStartup() {
        LockScreenAlarmActivity.applyScreenshotProtection(null, true);
    }
}
