package es.usc.citius.servando.calendula.healthcareprovider.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import es.usc.citius.servando.calendula.persistence.Patient
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Synthetic patient IDs and medicine codes only; no real clinical data. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ActiveMedPatientOwnershipTest {

    private fun patient(id: Long?, code: String?): Patient = Patient().apply {
        this.id = id
        this.code = code
    }

    @Test
    fun recycledNumericPatientIdCannotAuthorizeStaleProviderSnapshot() {
        val original = patient(101L, "synthetic-original")
        val restoredOtherPatient = patient(101L, "synthetic-replacement")
        val sameOwnerAfterReload = patient(101L, "synthetic-original")
        assertTrue(ActiveMedUpdater.sameStablePatient(original, sameOwnerAfterReload))
        assertFalse(ActiveMedUpdater.sameStablePatient(original, restoredOtherPatient))
        assertFalse(ActiveMedUpdater.incomingOwnersMatchStablePatient(
            restoredOtherPatient, listOf(original)))
        assertTrue(ActiveMedUpdater.incomingOwnersMatchStablePatient(
            original, listOf(original, sameOwnerAfterReload)))
        assertTrue(ActiveMedUpdater.incomingOwnersMatchStablePatient(
            original, emptyList()))
    }

    @Test
    fun ownerlessOrForeignParsedProviderRowsFailClosed() {
        val selected = patient(201L, "synthetic-selected")
        assertFalse(ActiveMedUpdater.incomingOwnersMatchStablePatient(
            selected, listOf(patient(201L, null))))
        assertFalse(ActiveMedUpdater.incomingOwnersMatchStablePatient(
            selected, listOf(patient(201L, ""))))
        assertFalse(ActiveMedUpdater.incomingOwnersMatchStablePatient(
            selected, listOf(patient(201L, "  "))))
        assertFalse(ActiveMedUpdater.incomingOwnersMatchStablePatient(
            selected, listOf(patient(202L, "synthetic-selected"))))
        assertFalse(ActiveMedUpdater.incomingOwnersMatchStablePatient(
            selected, listOf(null)))
        assertFalse(ActiveMedUpdater.incomingOwnersMatchStablePatient(
            patient(0L, "synthetic-selected"), emptyList()))
        assertFalse(ActiveMedUpdater.incomingOwnersMatchStablePatient(
            null, emptyList()))
    }

    @Test
    fun samePatientNewAndExistingMedicationCanBeImported() {
        assertTrue(ActiveMedUpdater.incomingOwnersMatchActivePatient(
            101L, listOf(101L, 101L)))
        assertTrue(ActiveMedUpdater.incomingOwnersMatchActivePatient(
            101L, emptyList()))
    }

    @Test
    fun missingOrTransientActivePatientNeverAdmitsProviderImport() {
        assertFalse(ActiveMedUpdater.incomingOwnersMatchActivePatient(
            null, listOf(101L)))
        assertFalse(ActiveMedUpdater.incomingOwnersMatchActivePatient(
            0L, listOf(101L)))
        assertFalse(ActiveMedUpdater.incomingOwnersMatchActivePatient(
            -1L, emptyList()))
    }

    @Test
    fun importedMedicationFromOtherPatientIsRejectedBeforeMutation() {
        assertFalse(ActiveMedUpdater.incomingOwnersMatchActivePatient(
            101L, listOf(102L)))
        assertFalse(ActiveMedUpdater.incomingOwnersMatchActivePatient(
            101L, listOf(101L, 102L)))
        assertFalse(ActiveMedUpdater.incomingOwnersMatchActivePatient(
            101L, listOf(null)))
    }

    @Test
    fun sameMedicineCodeIsValidForDifferentPatientsSeparately() {
        // A provider code is not a globally unique patient identifier.
        // Each individual snapshot belongs entirely to its own patient.
        assertTrue(ActiveMedUpdater.incomingOwnersMatchActivePatient(
            101L, listOf(101L)))
        assertTrue(ActiveMedUpdater.incomingOwnersMatchActivePatient(
            102L, listOf(102L)))
        assertTrue(ActiveMedUpdater.hasUniqueUsableCodes(listOf("same-code")))
    }

    @Test
    fun duplicateOrMissingCodesInOneSnapshotAreRejected() {
        assertTrue(ActiveMedUpdater.hasUniqueUsableCodes(
            listOf("code-one", "code-two")))
        assertTrue(ActiveMedUpdater.hasUniqueUsableCodes(emptyList()))
        assertFalse(ActiveMedUpdater.hasUniqueUsableCodes(
            listOf("same-code", "same-code")))
        assertFalse(ActiveMedUpdater.hasUniqueUsableCodes(
            listOf("valid-code", "")))
        assertFalse(ActiveMedUpdater.hasUniqueUsableCodes(
            listOf("valid-code", null)))
        assertFalse(ActiveMedUpdater.hasUniqueUsableCodes(
            listOf("   ")))
    }

    @Test
    fun multipleOwnedProviderLinksAreAllowedButForeignOrMissingOwnersAreRejected() {
        assertTrue(ActiveMedUpdater.allLinksBelongToPatient(101L, emptyList()))
        assertTrue(ActiveMedUpdater.allLinksBelongToPatient(101L, listOf(101L)))
        assertTrue(ActiveMedUpdater.allLinksBelongToPatient(101L, listOf(101L, 101L)))
        assertTrue(ActiveMedUpdater.allLinksBelongToPatient(101L, listOf(101L, 101L, 101L)))
        assertFalse(ActiveMedUpdater.allLinksBelongToPatient(101L, listOf(101L, 102L)))
        assertFalse(ActiveMedUpdater.allLinksBelongToPatient(101L, listOf(102L)))
        assertFalse(ActiveMedUpdater.allLinksBelongToPatient(101L, listOf(null)))
        assertFalse(ActiveMedUpdater.allLinksBelongToPatient(0L, emptyList()))
        assertFalse(ActiveMedUpdater.allLinksBelongToPatient(null, emptyList()))
    }

    @Test
    fun switchingSelectedPatientRejectsPreviouslyParsedSnapshot() {
        assertFalse(ActiveMedUpdater.incomingOwnersMatchActivePatient(
            102L, listOf(101L)))
    }
}
