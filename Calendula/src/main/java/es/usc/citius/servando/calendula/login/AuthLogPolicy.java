/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.login;

import es.usc.citius.servando.calendula.util.LogUtil;

/** Prevent OAuth, JWT, provider responses and patient identifiers in Logcat. */
public final class AuthLogPolicy {
    public enum Failure {
        STATE_PARSING,
        AUTHORIZATION_RESPONSE,
        AUTH_CODE_EXCHANGE,
        CALLBACK,
        ID_TOKEN,
        PATIENT_PERSISTENCE,
        TOKEN_VERIFICATION
    }

    private AuthLogPolicy() {
    }

    static String safeMessage(Failure failure) {
        switch (failure) {
            case STATE_PARSING:
                return "Authentication state is invalid";
            case AUTHORIZATION_RESPONSE:
                return "Authorization response failed";
            case AUTH_CODE_EXCHANGE:
                return "Authorization code exchange failed";
            case CALLBACK:
                return "Authentication callback failed";
            case ID_TOKEN:
                return "Identity token validation failed";
            case PATIENT_PERSISTENCE:
                return "Could not initialize local patient record";
            case TOKEN_VERIFICATION:
                return "Authentication signature/claims validation failed";
            default:
                return "Authentication operation failed";
        }
    }

    public static void logFailure(String tag, Failure category, Throwable ignoredSensitiveCause) {
        // A Throwable may contain an access token, an authorization code,
        // server response JSON, URL parameters or a patient identifier.
        // Never print its message or stack trace, even in debug builds.
        LogUtil.e(tag, safeMessage(category));
    }
}
