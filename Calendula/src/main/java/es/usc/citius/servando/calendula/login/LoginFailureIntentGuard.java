package es.usc.citius.servando.calendula.login;

import android.content.Intent;

/**
 * A login failure Intent is diagnostic input, not trusted identity data.
 * Only the exact auth-level sentinel may affect persisted UI state.
 */
final class LoginFailureIntentGuard {
    private LoginFailureIntentGuard() {
    }

    static boolean isAuthLevelFailure(Intent intent) {
        if (intent == null) {
            return false;
        }
        try {
            return LoginActivity.LOGIN_FAIL_REASON_AUTH_LEVEL.equals(
                    intent.getStringExtra(LoginActivity.EXTRA_LOGIN_FAILED_REASON));
        } catch (RuntimeException malformed) {
            // Corrupt/wrong-typed Parcelable extras must not crash login.
            return false;
        }
    }
}
