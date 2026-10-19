package es.usc.citius.servando.calendula.healthcareprovider.fhir

import org.hl7.fhir.dstu3.model.MedicationRequest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A damaged provider record must never be silently removed from the list
 * before medication reconciliation (which could mark a real dose discontinued).
 * Synthetic FHIR only; no patient database or remote service is used.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FhirSnapshotCompletenessTest {

    @Test
    fun corruptedRecordCausesWholeProviderSnapshotToFail() {
        val response = ResponseVO().apply {
            medicationRequests = mapOf("broken" to MedicationRequest())
            medications = emptyMap()
            medicationDispenses = emptyMap()
        }

        try {
            FHIRUtil.genVOsFromResponse(response)
            throw AssertionError("A partially parsed medication snapshot cannot be accepted")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message?.contains("invalid record") == true)
        }
    }

    @Test
    fun legitimatelyEmptyProviderSnapshotStillParsesAsEmpty() {
        val response = ResponseVO().apply {
            medicationRequests = emptyMap()
            medications = emptyMap()
            medicationDispenses = emptyMap()
        }
        assertTrue(FHIRUtil.genVOsFromResponse(response).isEmpty())
    }
    @Test
    fun rejectedSnapshotDoesNotPoisonSubsequentEmptySnapshot() {
        val invalid = ResponseVO().apply {
            medicationRequests = linkedMapOf(
                "first-broken" to MedicationRequest(),
                "second-broken" to MedicationRequest()
            )
            medications = emptyMap()
            medicationDispenses = emptyMap()
        }
        try {
            FHIRUtil.genVOsFromResponse(invalid)
            throw AssertionError("An invalid provider snapshot must fail completely")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message?.contains("invalid record") == true)
        }

        val validEmpty = ResponseVO().apply {
            medicationRequests = emptyMap()
            medications = emptyMap()
            medicationDispenses = emptyMap()
        }
        assertTrue(FHIRUtil.genVOsFromResponse(validEmpty).isEmpty())
    }

}
