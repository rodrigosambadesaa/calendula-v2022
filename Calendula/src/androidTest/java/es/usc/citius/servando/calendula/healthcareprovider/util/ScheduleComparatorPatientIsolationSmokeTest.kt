package es.usc.citius.servando.calendula.healthcareprovider.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import es.usc.citius.servando.calendula.database.DB
import es.usc.citius.servando.calendula.healthcareprovider.model.ActiveMedDCPFVO
import es.usc.citius.servando.calendula.healthcareprovider.model.ActiveMedVisualizationType
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedEntity
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedType
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DosageEntity
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DosageType
import es.usc.citius.servando.calendula.persistence.Medicine
import es.usc.citius.servando.calendula.persistence.Patient
import es.usc.citius.servando.calendula.persistence.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Synthetic records exercise real SQLite owner-scoped lookup, not just a
 * pure predicate; no production patient or prescription data is used.
 */
@RunWith(AndroidJUnit4::class)
class ScheduleComparatorPatientIsolationSmokeTest {

    @Test
    fun sameCodeForTwoPatientsUsesOnlyScheduleOwnersProviderRecord() {
        assertTrue(DB.initialized)
        val suffix = System.nanoTime().toString()
        val code = "ci-schedule-owner-code-$suffix"
        val firstPatient = patient("first-$suffix")
        val secondPatient = patient("second-$suffix")
        val firstDose = DosageEntity().apply { type = DosageType.AS_NEEDED }
        val secondDose = DosageEntity().apply { type = DosageType.AS_NEEDED }
        val firstMed = activeMed(code, firstPatient, firstDose)
        val secondMed = activeMed(code, secondPatient, secondDose)
        try {
            assertEquals(1, DB.patients().create(firstPatient))
            assertEquals(1, DB.patients().create(secondPatient))
            assertEquals(1, DB.healthcareProviderDB().dosages().create(firstDose))
            assertEquals(1, DB.healthcareProviderDB().dosages().create(secondDose))
            assertEquals(1, DB.healthcareProviderDB().activeMeds().create(firstMed))
            assertEquals(1, DB.healthcareProviderDB().activeMeds().create(secondMed))

            val localMedicine = Medicine("Synthetic scheduled medicine").apply {
                patient = secondPatient
                activeMedId = secondMed.id
            }
            val schedule = Schedule(localMedicine).apply { patient = secondPatient }

            assertTrue(ScheduleComparator.checkActiveMed(
                ActiveMedDCPFVO(secondMed), schedule).isEmpty())

            localMedicine.activeMedId = firstMed.id
            assertEquals(1, ScheduleComparator.checkActiveMed(
                ActiveMedDCPFVO(secondMed), schedule).size)

            // A missing provider row is a discrepancy, not a null dereference.
            val missing = activeMed("absent-$suffix", secondPatient, secondDose)
            assertEquals(1, ScheduleComparator.checkActiveMed(
                ActiveMedDCPFVO(missing), schedule).size)
        } finally {
            for (med in listOf(firstMed, secondMed)) {
                med.id?.let { id ->
                    if (DB.healthcareProviderDB().activeMeds().findById(id) != null) {
                        DB.healthcareProviderDB().activeMeds().deleteById(id)
                    }
                }
            }
            for (dose in listOf(firstDose, secondDose)) {
                dose.id?.let { id ->
                    if (DB.healthcareProviderDB().dosages().findById(id) != null) {
                        DB.healthcareProviderDB().dosages().deleteById(id)
                    }
                }
            }
            for (owner in listOf(firstPatient, secondPatient)) {
                owner.id?.let { id ->
                    if (DB.patients().findById(id) != null) DB.patients().deleteById(id)
                }
            }
        }
    }

    private fun patient(id: String): Patient = Patient().apply {
        code = "ci-comparator-$id"
        name = "Synthetic comparator owner $id"
    }

    private fun activeMed(code: String, owner: Patient, dose: DosageEntity): ActiveMedEntity =
        ActiveMedEntity().apply {
            this.code = code
            patient = owner
            dosage = dose
            type = ActiveMedType.DCPF
            visualizationType = ActiveMedVisualizationType.INSULIN_TYPE
            defaultDisplay = "Synthetic provider medication"
        }
}
