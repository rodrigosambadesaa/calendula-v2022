package es.usc.citius.servando.calendula.login;

import com.google.gson.Gson;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.jwt.SignedJWT;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
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
    private static final String SETTING_EXPECTED_VALUE = "yes";
    private static final String SERVER_KEY_FILENAME = "server_keys.json";
    private static Boolean verifyToken;

    /**
     * Verifies a signed JWT
     *
     * @param signedJWT the jwt
     * @return <code>true</code> if correctly verified, <code>false</code> otherwise
     */
    public static boolean verifyToken(final SignedJWT signedJWT) {

        if (!BuildConfig.LOGIN_TOKEN_VERIFY) {
            LogUtil.d(TAG, "verifyToken: Skipping token verification.");
            return true;
        }

        final List<JWTPublicKey> publicKeys = getPublicKeys();

        final boolean ret = verifyWithKeyList(signedJWT, publicKeys);

        LogUtil.d(TAG, "verifyToken() returned: " + ret);
        return ret;
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
