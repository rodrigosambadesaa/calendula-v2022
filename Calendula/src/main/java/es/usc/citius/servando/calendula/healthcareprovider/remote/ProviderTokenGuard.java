/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.healthcareprovider.remote;

/** Fail closed before attaching malformed credentials to provider requests. */
public final class ProviderTokenGuard {
    private ProviderTokenGuard() {}

    public static boolean isUsable(String accessToken) {
        if (accessToken == null || accessToken.isEmpty()) {
            return false;
        }
        // OAuth Bearer credentials may not contain whitespace or HTTP header
        // control characters. Do not normalize them: changing the token could
        // authenticate a different identity or break its signature.
        for (int i = 0; i < accessToken.length(); i++) {
            char c = accessToken.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c)) {
                return false;
            }
        }
        return true;
    }
}
