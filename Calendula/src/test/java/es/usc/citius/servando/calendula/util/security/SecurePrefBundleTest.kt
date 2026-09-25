/*
 *    Calendula - An assistant for personal medication management.
 *    Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 *    Calendula is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 3 of the License, or
 *    (at your option) any later version.
 */

package es.usc.citius.servando.calendula.util.security

import android.os.Build
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P])
class SecurePrefBundleTest {

    @Before
    fun setUp() {
        SecurePrefBundle.delete()
    }

    @After
    fun tearDown() {
        SecurePrefBundle.delete()
    }

    @Test
    fun patientLinkTokenCanBeStoredAndCleared() {
        SecurePrefBundle
            .setPatientLinkToken(42L, "secret-token")
            .apply()

        assertEquals("secret-token", SecurePrefBundle.getPatientLinkToken(42L))

        SecurePrefBundle
            .clearPatientLinkToken(42L)
            .apply()

        assertNull(SecurePrefBundle.getPatientLinkToken(42L))
    }

    @Test
    fun patientLinkTokensAreSeparatedByPatientId() {
        SecurePrefBundle
            .setPatientLinkToken(1L, "token-one")
            .setPatientLinkToken(2L, "token-two")
            .apply()

        assertEquals("token-one", SecurePrefBundle.getPatientLinkToken(1L))
        assertEquals("token-two", SecurePrefBundle.getPatientLinkToken(2L))
    }

    @Test
    fun deletingSecureBundleClearsPatientLinkTokensInMemory() {
        SecurePrefBundle
            .setPatientLinkToken(7L, "token-seven")
            .apply()

        SecurePrefBundle.delete()

        assertNull(SecurePrefBundle.getPatientLinkToken(7L))
    }
}
