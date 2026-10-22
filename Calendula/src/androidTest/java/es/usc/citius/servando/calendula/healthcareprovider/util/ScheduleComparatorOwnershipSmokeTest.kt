package es.usc.citius.servando.calendula.healthcareprovider.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import es.usc.citius.servando.calendula.database.DB
import es.usc.citius.servando.calendula.healthcareprovider.model.ActiveMedDCPFVO
import es.usc.citius.servando.calendula.persistence.Medicine
import es.usc.citius.servando.calendula.persistence.Patient
import es.usc.citius.servando.calendula.persistence.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** Real SQLite owners and synthetic unpersisted schedule: no clinical data. */
@RunWith(AndroidJUnit4::class)
class ScheduleComparatorOwnershipSmokeTest {
    @Test
    fun rejectWrongOwnerAndOwnerlessLinkedMedicineBeforeComparingDoses() {
        assertTrue(DB.initialized)
        val suffix = System.nanoTime()
        val a = Patient().apply {
            code = "ci-compare-owner-a-$suffix"
            name = "Synthetic patient A"
        }
        val b = Patient().apply {
            code = "ci-compare-owner-b-$suffix"
            name = "Synthetic patient B"
        }
        try {
            assertEquals(1, DB.patients().create(a))
            assertEquals(1, DB.patients().create(b))
            val provider = ActiveMedDCPFVO(a, "ci-medicine-$suffix",
                "Synthetic provider medication", emptyList(), null)

            val wrongOwnerSchedule = Schedule().apply { patient = b }
            assertRejected(provider, wrongOwnerSchedule)

            val ownerlessLocalMedicine = Medicine("Synthetic local medicine")
            val ownerlessLink = Schedule(ownerlessLocalMedicine).apply { patient = a }
            assertRejected(provider, ownerlessLink)

            val crossPatientLocalMedicine = Medicine("Synthetic local medicine").apply {
                patient = b
            }
            val crossPatientLink = Schedule(crossPatientLocalMedicine).apply {
                patient = a
            }
            assertRejected(provider, crossPatientLink)
        } finally {
            for (patient in listOf(a, b)) {
                patient.id?.let { id ->
                    if (DB.patients().findById(id) != null) DB.patients().remove(patient)
                }
            }
        }
    }

    private fun assertRejected(
        imported: ActiveMedDCPFVO, schedule: Schedule
    ) {
        try {
            ScheduleComparator.compare(imported, schedule)
            fail("A mismatched schedule must not be compared against a patient's doses")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message?.contains("ownership mismatch") == true)
        }
    }
}
