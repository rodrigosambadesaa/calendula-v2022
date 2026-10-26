package es.usc.citius.servando.calendula.healthcareprovider.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import es.usc.citius.servando.calendula.database.DB
import es.usc.citius.servando.calendula.healthcareprovider.model.ActiveMedVisualizationType
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedEntity
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedType
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DosageEntity
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DosageType
import es.usc.citius.servando.calendula.persistence.Medicine
import es.usc.citius.servando.calendula.persistence.Patient
import es.usc.citius.servando.calendula.persistence.Schedule
import es.usc.citius.servando.calendula.scheduling.model.EventInstance
import es.usc.citius.servando.calendula.scheduling.model.EventType
import org.joda.time.DateTime
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Two same-patient schedules must both be invalidated after an official update. */
@RunWith(AndroidJUnit4::class)
class MultipleProviderLinksUpdateSmokeTest {
    @Test
    fun providerUpdateFlagsAllOwnedSchedulesAndRemovesFutureIntakes() {
        assertTrue(DB.initialized)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val patient = Patient().apply {
            code = "ci-multi-update-owner-" + System.nanoTime()
            name = "Synthetic update owner"
        }
        val dose = DosageEntity().apply { type = DosageType.AS_NEEDED }
        val provider = ActiveMedEntity().apply {
            code = "ci-multi-update-provider-" + System.nanoTime()
            this.patient = patient
            type = ActiveMedType.DCPF
            visualizationType = ActiveMedVisualizationType.INSULIN_TYPE
            defaultDisplay = "Synthetic updated provider medication"
            dosage = dose
        }
        val first = Medicine("Synthetic update local 1").apply { this.patient = patient }
        val second = Medicine("Synthetic update local 2").apply { this.patient = patient }
        val schedules = mutableListOf<Schedule>()
        val futureIntakes = mutableListOf<EventInstance>()
        try {
            assertEquals(1, DB.patients().create(patient))
            assertEquals(1, DB.healthcareProviderDB().dosages().create(dose))
            assertEquals(1, DB.healthcareProviderDB().activeMeds().create(provider))
            for (medicine in listOf(first, second)) {
                medicine.activeMedId = provider.id
                assertEquals(1, DB.medicines().create(medicine))
                val schedule = Schedule(medicine).apply {
                    this.patient = patient
                    activeMedId = provider.id
                }
                assertEquals(1, DB.schedules().create(schedule))
                schedules.add(schedule)
                val next = EventInstance(
                    DateTime.now().plusDays(4).withMillisOfSecond(0),
                    EventType.MEDICATION_INTAKE
                ).apply {
                    this.patient = patient
                    ref = schedule.id
                    addParam(EventInstance.PARAM_DOSE, 1.0)
                }
                assertEquals(1, DB.eventInstances().create(next))
                futureIntakes.add(next)
            }
            ActiveMedUpdater.setPendingReview(context, provider)
            for (schedule in schedules) {
                val saved = DB.schedules().findById(schedule.id)
                assertNotNull(saved)
                assertEquals(provider.id, saved.activeMedId)
                assertTrue(saved.hasState(Schedule.ScheduleState.PENDING_REVIEW_AFTER_UPDATE))
            }
            for (event in futureIntakes) {
                assertNull("No future intakes should survive an updated schedule",
                    DB.eventInstances().findById(event.id))
            }
            assertNotNull(DB.healthcareProviderDB().activeMeds().findById(provider.id))
            assertNotNull(DB.medicines().findById(first.id))
            assertNotNull(DB.medicines().findById(second.id))
        } finally {
            for (event in futureIntakes) {
                event.id?.let { id ->
                    if (DB.eventInstances().findById(id) != null)
                        DB.eventInstances().remove(event)
                }
            }
            for (schedule in schedules) {
                schedule.id?.let { id ->
                    if (DB.schedules().findById(id) != null)
                        DB.schedules().remove(schedule)
                }
            }
            for (medicine in listOf(first, second)) {
                medicine.id?.let { id ->
                    if (DB.medicines().findById(id) != null)
                        DB.medicines().deleteById(id)
                }
            }
            provider.id?.let { id ->
                if (DB.healthcareProviderDB().activeMeds().findById(id) != null)
                    DB.healthcareProviderDB().activeMeds().deleteById(id)
            }
            dose.id?.let { id ->
                if (DB.healthcareProviderDB().dosages().findById(id) != null)
                    DB.healthcareProviderDB().dosages().deleteById(id)
            }
            patient.id?.let { id ->
                if (DB.patients().findById(id) != null) DB.patients().deleteById(id)
            }
        }
    }
}
