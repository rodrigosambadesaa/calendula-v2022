/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Real Android SQLite regression: another patient's intake event must not
 * appear in the schedule owner's home agenda. Synthetic disposable data only.
 */
@RunWith(AndroidJUnit4.class)
public class DailyAgendaPatientIsolationSmokeTest {

    @Test
    public void persistedCrossPatientEventAndMalformedDoseCannotBeRendered() throws Exception {
        assertTrue(DB.initialized);
        long seed = System.nanoTime();
        Patient a = new Patient();
        a.setCode("ci-agenda-owner-" + seed);
        a.setName("Synthetic agenda patient A");
        Patient b = new Patient();
        b.setCode("ci-agenda-other-" + seed);
        b.setName("Synthetic agenda patient B");
        Medicine medicine = new Medicine("Synthetic tablet A", Presentation.PILLS);
        Schedule schedule = new Schedule(medicine);
        EventInstance correct = new EventInstance(DateTime.now().plusHours(6),
                EventType.MEDICATION_INTAKE);
        EventInstance mismatched = new EventInstance(correct.getTime(),
                EventType.MEDICATION_INTAKE);
        EventInstance malformed = new EventInstance(correct.getTime(),
                EventType.MEDICATION_INTAKE);
        try {
            DB.patients().create(a);
            DB.patients().create(b);
            medicine.setPatient(a);
            DB.medicines().create(medicine);
            schedule.setPatient(a);
            DB.schedules().create(schedule);
            correct.setPatient(a);
            mismatched.setPatient(b);
            malformed.setPatient(a);
            for (EventInstance event : new EventInstance[]{correct, mismatched, malformed}) {
                event.setRef(schedule.getId());
            }
            correct.addParam(EventInstance.PARAM_DOSE, 1.5d);
            mismatched.addParam(EventInstance.PARAM_DOSE, 1.5d);
            malformed.addParam(EventInstance.PARAM_DOSE, "NaN");
            DB.eventInstances().save(correct);
            DB.eventInstances().save(mismatched);
            DB.eventInstances().save(malformed);

            Schedule storedSchedule = DB.schedules().findById(schedule.getId());
            EventInstance validStored = DB.eventInstances().findById(correct.getId());
            EventInstance wrongStored = DB.eventInstances().findById(mismatched.getId());
            EventInstance invalidStored = DB.eventInstances().findById(malformed.getId());
            assertNotNull(storedSchedule);
            assertNotNull(validStored);
            assertNotNull(wrongStored);
            assertNotNull(invalidStored);
            assertEquals(Double.valueOf(1.5d),
                    DailyAgendaEventValidator.verifiedDose(validStored, storedSchedule));
            assertNull("Other patient's intake must never display under profile A",
                    DailyAgendaEventValidator.verifiedDose(wrongStored, storedSchedule));
            assertNull("Nonfinite dose must never display as a valid amount",
                    DailyAgendaEventValidator.verifiedDose(invalidStored, storedSchedule));
        } finally {
            for (EventInstance event : new EventInstance[]{correct, mismatched, malformed}) {
                if (event.getId() != null && DB.eventInstances().findById(event.getId()) != null) {
                    DB.eventInstances().remove(event);
                }
            }
            if (schedule.getId() != null && DB.schedules().findById(schedule.getId()) != null) {
                DB.schedules().remove(schedule);
            }
            if (medicine.getId() != null && DB.medicines().findById(medicine.getId()) != null) {
                DB.medicines().remove(medicine);
            }
            if (a.getId() != null && DB.patients().findById(a.getId()) != null) {
                DB.patients().remove(a);
            }
            if (b.getId() != null && DB.patients().findById(b.getId()) != null) {
                DB.patients().remove(b);
            }
        }
    }
}
