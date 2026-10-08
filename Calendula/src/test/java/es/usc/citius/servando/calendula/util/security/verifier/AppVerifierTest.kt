/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util.security.verifier

import org.junit.Assert.assertEquals
import org.junit.Test

class AppVerifierTest {

    @Test
    fun suspiciousEnvironmentIsReportedWithoutRequiringDatabaseInitialization() {
        // A false positive must not access or wipe the database or vault.
        val result = AppVerifier.evaluateResults(
            listOf(VerificationResult(VerificationResult.Level.SECURITY_BREACH, "Warning"))
        )
        assertEquals(VerificationResult.Level.SECURITY_BREACH, result.level)
        assertEquals("\n ● Warning", result.message)
    }

    @Test
    fun breachWinsOverNonCriticalWarning() {
        val result = AppVerifier.evaluateResults(
            listOf(
                VerificationResult(VerificationResult.Level.SECURITY_WARNING, "Minor"),
                VerificationResult(VerificationResult.Level.SECURITY_BREACH, "Critical")
            )
        )
        assertEquals(VerificationResult.Level.SECURITY_BREACH, result.level)
        assertEquals("\n ● Critical", result.message)
    }

    @Test
    fun warningsRemainNonBlocking() {
        val result = AppVerifier.evaluateResults(
            listOf(VerificationResult(VerificationResult.Level.SECURITY_WARNING, "Advisory"))
        )
        assertEquals(VerificationResult.Level.SECURITY_WARNING, result.level)
    }

    @Test
    fun noVerificationFindingsRemainSafe() {
        val result = AppVerifier.evaluateResults(emptyList())
        assertEquals(VerificationResult.Level.SECURITY_OK, result.level)
    }
}
