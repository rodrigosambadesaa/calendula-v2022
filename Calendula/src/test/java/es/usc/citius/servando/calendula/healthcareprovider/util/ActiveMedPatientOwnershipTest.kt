package es.usc.citius.servando.calendula.healthcareprovider.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Fake patient IDs only; no real clinical, credential or profile values. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ActiveMedPatientOwnershipTest {

    @Test
    fun samePatientNewAndExistingMedicationCanBeImported() {
        assertTrue(ActiveMedUpdater.ownersAreConsistent(
            101L, listOf(101L, 101L), listOf(101L)))
        assertTrue(ActiveMedUpdater.ownersAreConsistent(
            101L, emptyList(), emptyList()))
    }

    @Test
    fun missingOrTransientActivePatientNeverAdmitsProviderImport() {
        assertFalse(ActiveMedUpdater.ownersAreConsistent(
            null, listOf(101L), emptyList()))
        assertFalse(ActiveMedUpdater.ownersAreConsistent(
            0L, listOf(101L), emptyList()))
        assertFalse(ActiveMedUpdater.ownersAreConsistent(
            -1L, emptyList(), emptyList()))
    }

    @Test
    fun importedMedicationFromOtherPatientIsRejectedBeforeMutation() {
        assertFalse(ActiveMedUpdater.ownersAreConsistent(
            101L, listOf(102L), emptyList()))
        assertFalse(ActiveMedUpdater.ownersAreConsistent(
            101L, listOf(101L, 102L), emptyList()))
        assertFalse(ActiveMedUpdater.ownersAreConsistent(
            101L, listOf(null), emptyList()))
    }

    @Test
    fun sameCodeAlreadyStoredByAnotherPatientMustFailClosed() {
        assertFalse(ActiveMedUpdater.ownersAreConsistent(
            101L, listOf(101L), listOf(102L)))
        assertFalse(ActiveMedUpdater.ownersAreConsistent(
            101L, listOf(101L), listOf(null)))
        assertFalse(ActiveMedUpdater.ownersAreConsistent(
            101L, listOf(101L), listOf(101L, 102L)))
    }
}
