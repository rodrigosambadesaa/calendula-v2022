/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.pinlock;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mockStatic;

/**
 * Synthetic PIN timer; never reads or modifies real credentials or patient data.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class UnlockStateCorruptPreferenceTest {

    @Test
    public void wrongTypeRestoredTimeoutRevokesPreviouslyUnlockedSession() {
        UnlockStateManager state = UnlockStateManager.getInstance();
        state.lock();
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getString(
                    PreferenceKeys.UNLOCK_PIN_TIMEOUT, "120"))
                    .thenThrow(new ClassCastException("Synthetic restored Boolean"));
            state.unlock();
            assertFalse("An unreadable timeout must fail closed instead of crashing",
                    state.isUnlocked());
        } finally {
            state.lock();
        }
    }

    @Test
    public void sessionCannotReopenWithoutNewAuthenticationAfterRepair() {
        UnlockStateManager state = UnlockStateManager.getInstance();
        state.lock();
        try (MockedStatic<PreferenceUtils> prefs = mockStatic(PreferenceUtils.class)) {
            prefs.when(() -> PreferenceUtils.getString(
                    PreferenceKeys.UNLOCK_PIN_TIMEOUT, "120"))
                    .thenThrow(new ClassCastException("Synthetic restored Integer"))
                    .thenReturn("120");
            state.unlock();
            assertFalse(state.isUnlocked());
            assertFalse("Fixing the preference cannot unlock an expired session",
                    state.isUnlocked());
            state.unlock();
            assertTrue("New authentication still works with a valid timeout",
                    state.isUnlocked());
        } finally {
            state.lock();
        }
    }
}
