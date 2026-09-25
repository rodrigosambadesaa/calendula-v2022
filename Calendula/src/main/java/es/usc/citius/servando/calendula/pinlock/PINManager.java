/*
 *    Calendula - An assistant for personal medication management.
 *    Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 *    Calendula is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this software.  If not, see <http://www.gnu.org/licenses/>.
 */

package es.usc.citius.servando.calendula.pinlock;

import android.content.SharedPreferences;
import android.util.Base64;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import es.usc.citius.servando.calendula.util.LogUtil;
import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.security.SecurePrefBundle;
import es.usc.citius.servando.calendula.util.security.SecuredVault;


public class PINManager {

    private static final String TAG = "PINManager";
    private static final String SALT_PATTERN = "%1$s%2$s";
    private static final String PBKDF2_PREFIX = "pbkdf2-sha1$";
    private static final int PBKDF2_ITERATIONS = 100000;
    private static final int PBKDF2_KEY_LENGTH_BITS = 256;
    private static final int SALT_LENGTH_BYTES = 20;
    private static SecureRandom random;

    /**
     * Checks if the PIN number matches the currently stored one.
     *
     * @param pin the PIN number
     * @return <code>true</code> if the PIN matches, <code>false</code> otherwise
     * @throws IllegalStateException if there's no PIN stored currently.
     */
    public static boolean checkPIN(final String pin) throws IllegalStateException {

        final String salt = SecurePrefBundle.INSTANCE.getPinSalt();
        final String storedHash = SecurePrefBundle.INSTANCE.getPinHash();
        if (salt == null || storedHash == null) {
            throw new IllegalStateException("No PIN currently stored!");
        }

        if (storedHash.startsWith(PBKDF2_PREFIX)) {
            return checkPbkdf2Pin(pin, salt, storedHash);
        }

        // Legacy versions stored raw SHA-256 bytes and random salt bytes directly
        // as Java Strings. Preserve that exact derivation only long enough to
        // validate an existing PIN, then migrate it transparently.
        final String salted = String.format(SALT_PATTERN, salt, pin);
        final String legacyHash = calculateLegacyHash(salted);
        if (legacyHash == null) {
            throw new RuntimeException("Failed to check PIN number");
        }

        final boolean matches = storedHash.equals(legacyHash);
        if (matches) {
            savePIN(pin);
        }
        return matches;
    }

    /**
     * Checks if PIN is set
     *
     * @return <code>true</code> if PIN is set, <code>false</code> otherwise
     */
    public static boolean isPINSet() {
        return SecurePrefBundle.INSTANCE.getPinHash() != null;
    }

    /**
     * Clears PIN info from prefs.
     */
    public static void clearPIN() {
        SharedPreferences.Editor edit = SecuredVault.INSTANCE.edit();
        edit.remove(PreferenceKeys.FINGERPRINT_ENABLED.key());
        edit.apply();

        SecurePrefBundle.INSTANCE
                .clearPinHash()
                .clearPinSalt()
                .apply();
    }

    /**
     * Stores the PIN number, overwriting the current one if present.
     *
     * @param pin the PIN number
     * @return <code>true</code> if saved correctly, <code>false</code> otherwise.
     */
    public static boolean savePIN(final String pin) {

        byte[] saltBytes = new byte[SALT_LENGTH_BYTES];
        getRandom().nextBytes(saltBytes);
        final String salt = Base64.encodeToString(saltBytes, Base64.NO_WRAP);

        try {
            final byte[] derived = derivePbkdf2(pin, saltBytes);
            final String hash = PBKDF2_PREFIX
                    + PBKDF2_ITERATIONS
                    + "$"
                    + Base64.encodeToString(derived, Base64.NO_WRAP);

            SecurePrefBundle.INSTANCE
                    .setPinHash(hash)
                    .setPinSalt(salt)
                    .apply();
            return true;
        } catch (GeneralSecurityException e) {
            LogUtil.e(TAG, "savePIN: failed to derive PIN hash", e);
            return false;
        }
    }

    private static SecureRandom getRandom() {
        if (random == null) {
            random = new SecureRandom();
        }
        return random;
    }

    private static boolean checkPbkdf2Pin(
            String pin,
            String encodedSalt,
            String storedHash) {
        try {
            final String[] parts = storedHash.split("\\$", -1);
            if (parts.length != 3 || !parts[0].equals("pbkdf2-sha1")) {
                return false;
            }

            final int iterations = Integer.parseInt(parts[1]);
            if (iterations != PBKDF2_ITERATIONS) {
                return false;
            }

            final byte[] salt = Base64.decode(encodedSalt, Base64.NO_WRAP);
            final byte[] expected = Base64.decode(parts[2], Base64.NO_WRAP);
            final byte[] actual = derivePbkdf2(pin, salt);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            LogUtil.e(TAG, "checkPIN: malformed or unsupported PIN hash", e);
            return false;
        }
    }

    private static byte[] derivePbkdf2(String pin, byte[] salt)
            throws GeneralSecurityException {
        final PBEKeySpec spec =
                new PBEKeySpec(
                        pin.toCharArray(),
                        salt,
                        PBKDF2_ITERATIONS,
                        PBKDF2_KEY_LENGTH_BITS);
        try {
            return SecretKeyFactory
                    .getInstance("PBKDF2WithHmacSHA1")
                    .generateSecret(spec)
                    .getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private static String calculateLegacyHash(String message) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(message.getBytes());
            byte byteData[] = md.digest();
            return new String(byteData);
        } catch (NoSuchAlgorithmException e) {
            LogUtil.e(TAG, "calculateLegacyHash: ", e);
            return null;
        }
    }

}
