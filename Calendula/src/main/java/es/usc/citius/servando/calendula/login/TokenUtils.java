package es.usc.citius.servando.calendula.login;

import com.google.gson.Gson;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Date;
import java.util.List;

import es.usc.citius.servando.calendula.BuildConfig;
import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.util.CloseableUtil;
import es.usc.citius.servando.calendula.util.LogUtil;

/**
 * Utils to manage JWT tokens
 * <p>
 */
public class TokenUtils {

    private static final String TAG = "TokenUtils";
    private static final String SERVER_KEY_FILENAME = "server_keys.json";
    static final long ID_TOKEN_CLOCK_SKEW_MS = 60_000L;

    /**
     * Verifies the JWT signature against the bundled provider keys.
     */
    public static boolean verifyToken(final SignedJWT signedJWT) {

        if (!BuildConfig.LOGIN_TOKEN_VERIFY) {
            LogUtil.d(TAG, "verifyToken: Skipping token signature verification.");
            return true;
        }

        final List<JWTPublicKey> publicKeys = getPublicKeys();

        final boolean ret = verifyWithKeyList(signedJWT, publicKeys);

        LogUtil.d(TAG, "verifyToken() returned: " + ret);
        return ret;
    }

    /**
     * Validates the ID-token claims that can be checked from Calendula's static legacy
     * OpenID configuration. Signature verification alone is insufficient: an otherwise
     * correctly signed token may be expired, not yet valid, or minted for another client.
     *
     * The legacy provider configuration does not expose a canonical issuer value, so `iss`
     * cannot be compared safely here without inventing configuration. Audience and temporal
     * claims are nevertheless validated strictly.
     */
    public static boolean validateIdTokenClaims(final SignedJWT signedJWT) {
        if (signedJWT == null) {
            return false;
        }
        try {
            return validateIdTokenClaims(
                    signedJWT.getJWTClaimsSet(),
                    OpenIdProviderConfiguration.instance().getClientId(),
                    new Date());
        } catch (Exception e) {
            LogUtil.e(TAG, "validateIdTokenClaims: malformed claims", e);
            return false;
        }
    }

    static boolean validateIdTokenClaims(
            final JWTClaimsSet claims,
            final String expectedClientId,
            final Date now) throws Exception {
        if (claims == null || expectedClientId == null || expectedClientId.isEmpty() || now == null) {
            return false;
        }

        final Date expiration = claims.getExpirationTime();
        if (expiration == null
                || expiration.getTime() + ID_TOKEN_CLOCK_SKEW_MS < now.getTime()) {
            return false;
        }

        final Date notBefore = claims.getNotBeforeTime();
        if (notBefore != null
                && notBefore.getTime() - ID_TOKEN_CLOCK_SKEW_MS > now.getTime()) {
            return false;
        }

        final Date issuedAt = claims.getIssueTime();
        if (issuedAt != null
                && issuedAt.getTime() - ID_TOKEN_CLOCK_SKEW_MS > now.getTime()) {
            return false;
        }

        final List<String> audience = claims.getAudience();
        if (audience == null || audience.isEmpty() || !audience.contains(expectedClientId)) {
            return false;
        }

        if (audience.size() > 1) {
            final String authorizedParty = claims.getStringClaim("azp");
            if (!expectedClientId.equals(authorizedParty)) {
                return false;
            }
        }

        return true;
    }

    private static boolean verifyWithKeyList(final SignedJWT signedJWT, final List<JWTPublicKey> publicKeys) {
        try {
            int i = 0;
            for (JWTPublicKey publicKey : publicKeys) {
                i++;
                BigInteger modulus = new Base64(publicKey.getN()).decodeToBigInteger();
                BigInteger exponent = new Base64(publicKey.getE()).decodeToBigInteger();

                RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
                KeyFactory factory = KeyFactory.getInstance("RSA");

                RSAPublicKey pubKey = (RSAPublicKey) factory.generatePublic(spec);
                JWSVerifier verifier = new RSASSAVerifier(pubKey);

                if (signedJWT.verify(verifier)) {
                    LogUtil.i(TAG, "verifyWithKeyList: Token verified (tried " + i + " keys)");
                    return true;
                } else {
                    LogUtil.w(TAG, "verifyWithKeyList: Failed to verify token (tried " + i + " keys)");
                }
            }
            return false;
        } catch (Exception e) {
            LogUtil.e(TAG, "verifyWithKeyList: ", e);
            return false;
        }
    }

    private static List<JWTPublicKey> getPublicKeys() {

        InputStream keyStream = null;
        try {
            keyStream = CalendulaApp.getContext().getAssets().open(SERVER_KEY_FILENAME);
            final JWTPublicKeys jwtPublicKeys = new Gson().fromJson(new InputStreamReader(keyStream), JWTPublicKeys.class);
            return jwtPublicKeys.getKeys();
        } catch (IOException e) {
            LogUtil.e(TAG, "getPublicKeys: ", e);
            throw new RuntimeException(e);
        } finally {
            CloseableUtil.closeQuietly(keyStream);
        }
    }
}
