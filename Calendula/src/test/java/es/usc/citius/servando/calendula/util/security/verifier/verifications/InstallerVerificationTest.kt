package es.usc.citius.servando.calendula.util.security.verifier.verifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Installer provenance must be an exact trusted package name, never a prefix. */
class InstallerVerificationTest {

    @Test
    fun acceptsOnlyExactGooglePlayInstaller() {
        assertTrue(InstallerVerification.isTrustedInstaller("com.android.vending"))
    }

    @Test
    fun rejectsInstallerPackageSpoofing() {
        assertFalse(InstallerVerification.isTrustedInstaller(null))
        assertFalse(InstallerVerification.isTrustedInstaller(""))
        assertFalse(InstallerVerification.isTrustedInstaller("com.android.vending.attacker"))
        assertFalse(InstallerVerification.isTrustedInstaller("com.android.vending2"))
        assertFalse(InstallerVerification.isTrustedInstaller("com.android.vending "))
        assertFalse(InstallerVerification.isTrustedInstaller("com.android.vending\\n"))
        assertFalse(InstallerVerification.isTrustedInstaller("COM.ANDROID.VENDING"))
        assertFalse(InstallerVerification.isTrustedInstaller("com.example.app"))
    }
}
