package es.usc.citius.servando.calendula.login;

import android.content.Intent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class LoginFailureIntentGuardTest {
    @Test
    public void exactAuthorizationLevelFailureIsRecognized() {
        Intent login = new Intent().putExtra(LoginActivity.EXTRA_LOGIN_FAILED_REASON,
                LoginActivity.LOGIN_FAIL_REASON_AUTH_LEVEL);
        assertTrue(LoginFailureIntentGuard.isAuthLevelFailure(login));
    }

    @Test
    public void missingAndWrongTypedReasonsNeverChangeAuthLevelState() {
        assertFalse(LoginFailureIntentGuard.isAuthLevelFailure(null));
        assertFalse(LoginFailureIntentGuard.isAuthLevelFailure(new Intent()));
        assertFalse(LoginFailureIntentGuard.isAuthLevelFailure(
                new Intent().putExtra(LoginActivity.EXTRA_LOGIN_FAILED_REASON, 3)));
        assertFalse(LoginFailureIntentGuard.isAuthLevelFailure(
                new Intent().putExtra(LoginActivity.EXTRA_LOGIN_FAILED_REASON,
                        "AUTH_LEVEL:foreign-provider-error")));
        assertFalse(LoginFailureIntentGuard.isAuthLevelFailure(
                new Intent().putExtra(LoginActivity.EXTRA_LOGIN_FAILED_REASON, "")));
    }

    @Test
    @Config(sdk = 33)
    public void wrongTypedIntentRemainsRejectedOnAndroid13() {
        assertFalse(LoginFailureIntentGuard.isAuthLevelFailure(
                new Intent().putExtra(LoginActivity.EXTRA_LOGIN_FAILED_REASON, 23L)));
        assertTrue(LoginFailureIntentGuard.isAuthLevelFailure(
                new Intent().putExtra(LoginActivity.EXTRA_LOGIN_FAILED_REASON,
                        LoginActivity.LOGIN_FAIL_REASON_AUTH_LEVEL)));
    }
}
