/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.pinlock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.security.MessageDigest;

import es.usc.citius.servando.calendula.util.PreferenceKeys;
import es.usc.citius.servando.calendula.util.PreferenceUtils;
import es.usc.citius.servando.calendula.util.security.SecurePrefBundle;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PINManagerTest {

    @Before
    public void setUp() {
        PINManager.clearPIN();
    }

    @After
    public void tearDown() {
        PINManager.clearPIN();
    }

    @Test
    public void clearPinAlsoDisablesFingerprintUnlock() {
        PreferenceUtils.edit()
                .putBoolean(PreferenceKeys.FINGERPRINT_ENABLED.key(), true)
                .commit();
        assertTrue(PreferenceUtils.getBoolean(PreferenceKeys.FINGERPRINT_ENABLED, false));

        PINManager.clearPIN();

        assertFalse(PreferenceUtils.getBoolean(PreferenceKeys.FINGERPRINT_ENABLED, false));
    }

    @Test
    public void newPinUsesVersionedPbkdf2Hash() {
        assertTrue(PINManager.savePIN("1234"));

        String storedHash = SecurePrefBundle.INSTANCE.getPinHash();
        assertTrue(storedHash.startsWith("pbkdf2-sha1$100000$"));
        assertTrue(PINManager.checkPIN("1234"));
        assertFalse(PINManager.checkPIN("4321"));
    }

    @Test
    public void legacyPinIsAcceptedAndMigratedAfterSuccessfulCheck() throws Exception {
        String pin = "2468";
        String legacySalt = "legacy-salt";
        String legacyHash = legacyHash(legacySalt, pin);

        SecurePrefBundle.INSTANCE
                .setPinSalt(legacySalt)
                .setPinHash(legacyHash)
                .apply();

        assertTrue(PINManager.checkPIN(pin));

        String migratedHash = SecurePrefBundle.INSTANCE.getPinHash();
        assertNotEquals(legacyHash, migratedHash);
        assertTrue(migratedHash.startsWith("pbkdf2-sha1$100000$"));
        assertTrue(PINManager.checkPIN(pin));
    }

    @Test
    public void wrongLegacyPinDoesNotMigrateHash() throws Exception {
        String legacySalt = "legacy-salt";
        String legacyHash = legacyHash(legacySalt, "2468");

        SecurePrefBundle.INSTANCE
                .setPinSalt(legacySalt)
                .setPinHash(legacyHash)
                .apply();

        assertFalse(PINManager.checkPIN("0000"));
        assertTrue(legacyHash.equals(SecurePrefBundle.INSTANCE.getPinHash()));
    }

    @Test
    public void missingPinInputIsRejectedWithoutMutatingStoredCredentials() {
        assertFalse(PINManager.savePIN(null));
        assertFalse(PINManager.savePIN(""));
        assertTrue(PINManager.savePIN("2468"));
        String hash = SecurePrefBundle.INSTANCE.getPinHash();
        assertFalse(PINManager.checkPIN(null));
        assertFalse(PINManager.checkPIN(""));
        assertTrue(hash.equals(SecurePrefBundle.INSTANCE.getPinHash()));
        assertTrue(PINManager.checkPIN("2468"));
    }

    @Test
    public void brokenSaltAndHashCannotTriggerInvalidPinAcceptance() {
        assertTrue(PINManager.savePIN("1357"));
        String correctSalt = SecurePrefBundle.INSTANCE.getPinSalt();
        String correctHash = SecurePrefBundle.INSTANCE.getPinHash();

        for (String invalidSalt : new String[]{"", "not base64!",
                "AAAA", new String(new char[256]).replace('\\0', 'A')}) {
            SecurePrefBundle.INSTANCE.setPinSalt(invalidSalt).apply();
            assertFalse("Malformed PIN salt was accepted", PINManager.checkPIN("1357"));
        }
        SecurePrefBundle.INSTANCE.setPinSalt(correctSalt).apply();

        for (String invalidHash : new String[]{"pbkdf2-sha1$100000$",
                "pbkdf2-sha1$100000$AAAA", "pbkdf2-sha1$99999$AAAA",
                "pbkdf2-sha1$100000$" + new String(new char[256]).replace('\\0', 'A')}) {
            SecurePrefBundle.INSTANCE.setPinHash(invalidHash).apply();
            assertFalse("Malformed PIN hash was accepted", PINManager.checkPIN("1357"));
        }
        SecurePrefBundle.INSTANCE.setPinHash(correctHash).apply();
        assertTrue("Valid stored credentials still work", PINManager.checkPIN("1357"));
    }

    private static String legacyHash(String salt, String pin) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update((salt + pin).getBytes());
        return new String(md.digest());
    }
}
