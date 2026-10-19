package es.usc.citius.servando.calendula.healthcareprovider.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import es.usc.citius.servando.calendula.database.DB
import es.usc.citius.servando.calendula.healthcareprovider.model.ActiveMedDCPFVO
import es.usc.citius.servando.calendula.persistence.Patient
import es.usc.citius.servando.calendula.util.PreferenceKeys
import es.usc.citius.servando.calendula.util.PreferenceUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real SQLite and preference checks using disposable synthetic patients.
 * Invalid provider snapshots must never mutate the medicine DB or revision.
 */
@RunWith(AndroidJUnit4::class)
class ProviderImportPatientPreflightSmokeTest {
    @Test
    fun crossPatientAndDuplicateSnapshotFailBeforeWriteOrHashAdvance() {
        assertTrue(DB.initialized)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previous = DB.patients().getActive(context)
        val suffix = System.nanoTime().toString()
        val code = "ci-import-preflight-$suffix"
        val first = patient("first-$suffix")
        val second = patient("second-$suffix")
        val prefs = PreferenceUtils.instance().preferences()
        val hashKey = PreferenceKeys.REMOTE_LAST_HASH.key()
        val originalHash = prefs.all[hashKey]
        try {
            assertEquals(1, DB.patients().create(first))
            assertEquals(1, DB.patients().create(second))
            DB.patients().setActive(first)

            val wrongPatient = ActiveMedDCPFVO(
                second, code, "Synthetic medication", emptyList(), null)
            val rejectedHash = "ci-rejected-other-owner-$suffix"
            try {
                ActiveMedUpdater.onNewMeds(
                    rejectedHash, listOf(wrongPatient), context, false)
                fail("An imported medication from another patient was accepted")
            } catch (expected: IllegalStateException) {
                // No write should have been reached.
            }
            assertNull(DB.healthcareProviderDB().activeMeds()
                .findByCodeForPatient(code, first))
            assertNull(DB.healthcareProviderDB().activeMeds()
                .findByCodeForPatient(code, second))
            assertFalse(prefs.all[hashKey] == rejectedHash)

            val firstRecord = ActiveMedDCPFVO(
                first, code, "Synthetic medication", emptyList(), null)
            val duplicateHash = "ci-rejected-duplicate-$suffix"
            try {
                ActiveMedUpdater.onNewMeds(
                    duplicateHash, listOf(firstRecord, firstRecord), context, false)
                fail("A duplicate-code snapshot was accepted")
            } catch (expected: IllegalStateException) {
                // Duplicate codes are rejected before they can be reconciled.
            }
            assertNull(DB.healthcareProviderDB().activeMeds()
                .findByCodeForPatient(code, first))
            assertFalse(prefs.all[hashKey] == duplicateHash)
        } finally {
            if (previous?.id != null && DB.patients().findById(previous.id) != null) {
                DB.patients().setActive(previous)
            } else {
                PreferenceUtils.edit()
                    .remove(PreferenceKeys.PATIENTS_ACTIVE.key()).commit()
            }
            for (owner in listOf(first, second)) {
                owner.id?.let { id ->
                    if (DB.patients().findById(id) != null) DB.patients().remove(owner)
                }
            }
            // No hash is deliberately changed by this test; signal a test
            // failure if anything unexpectedly altered the previous value.
            assertEquals(originalHash, prefs.all[hashKey])
        }
    }

    private fun patient(id: String): Patient = Patient().apply {
        code = "ci-provider-import-$id"
        name = "Synthetic provider import patient $id"
    }
}
