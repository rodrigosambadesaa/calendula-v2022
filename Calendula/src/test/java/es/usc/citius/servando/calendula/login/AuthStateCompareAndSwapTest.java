/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.login;

import net.openid.appauth.AuthState;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import es.usc.citius.servando.calendula.util.PreferenceUtils;
import es.usc.citius.servando.calendula.util.security.SecurePrefBundle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AuthStateCompareAndSwapTest {
    @Before
    public void setUp() {
        PreferenceUtils.init(RuntimeEnvironment.getApplication());
        SecurePrefBundle.INSTANCE.delete();
    }

    @After
    public void tearDown() {
        SecurePrefBundle.INSTANCE.delete();
    }

    @Test
    public void staleWorkerCannotRestoreCredentialsAfterLogoutOrSessionSwitch() {
        LoginStateManager manager = LoginStateManager.getInstance();
        AuthState refreshed = new AuthState();
        SecurePrefBundle.INSTANCE.setAuthState("session-before-refresh").apply();

        assertFalse(manager.updateAuthStateIfUnchanged("another-session", refreshed));
        assertEquals("session-before-refresh", SecurePrefBundle.INSTANCE.getAuthState());

        SecurePrefBundle.INSTANCE.clearAuthState().apply();
        assertFalse("A signed-out patient must remain signed out",
                manager.updateAuthStateIfUnchanged("session-before-refresh", refreshed));
        assertNull(SecurePrefBundle.INSTANCE.getAuthState());

        SecurePrefBundle.INSTANCE.setAuthState("new-patient-session").apply();
        assertFalse("A prior patient's worker cannot overwrite the new login",
                manager.updateAuthStateIfUnchanged("session-before-refresh", refreshed));
        assertEquals("new-patient-session", SecurePrefBundle.INSTANCE.getAuthState());

        assertTrue(manager.updateAuthStateIfUnchanged("new-patient-session", refreshed));
        assertEquals(refreshed.jsonSerializeString(), SecurePrefBundle.INSTANCE.getAuthState());
    }

    @Test
    public void competingRefreshesForSameSessionCanPublishOnlyOnce() throws Exception {
        LoginStateManager manager = LoginStateManager.getInstance();
        SecurePrefBundle.INSTANCE.setAuthState("session-initial").apply();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Boolean> refresh = () -> {
                start.await();
                return manager.updateAuthStateIfUnchanged("session-initial", new AuthState());
            };
            Future<Boolean> first = pool.submit(refresh);
            Future<Boolean> second = pool.submit(refresh);
            start.countDown();
            int successes = (first.get(5, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(5, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals("A refresh from the old session cannot overwrite its winner", 1, successes);
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }
}
