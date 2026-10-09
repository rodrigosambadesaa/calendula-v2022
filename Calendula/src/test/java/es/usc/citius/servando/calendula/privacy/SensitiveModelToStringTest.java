/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.privacy;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.PatientAllergen;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventType;
import es.usc.citius.servando.calendula.util.DailyAgendaItemStub;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Model debug representations must not leak patient, prescription, allergen or
 * exact intake timing data into crash reports or development diagnostics.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SensitiveModelToStringTest {

    private static final String PATIENT_SECRET = "PRIVATE-PATIENT-PERSON-38291";
    private static final String MEDICAL_SECRET = "PRIVATE-MEDICATION-DOSE-81729";
    private static final String ALLERGEN_SECRET = "PRIVATE-ALLERGEN-48127";

    private static void assertRedacted(String rendered) {
        assertNotNull(rendered);
        assertFalse("Patient identity leaked: " + rendered, rendered.contains(PATIENT_SECRET));
        assertFalse("Medication details leaked: " + rendered, rendered.contains(MEDICAL_SECRET));
        assertFalse("Allergy information leaked: " + rendered, rendered.contains(ALLERGEN_SECRET));
    }

    @Test
    public void patientAndAllergenDoNotPrintPersonalData() {
        Patient patient = new Patient();
        patient.setId(81729L);
        patient.setName(PATIENT_SECRET);
        patient.setCode(PATIENT_SECRET);
        patient.setAvatar(PATIENT_SECRET);
        assertRedacted(patient.toString());
        assertFalse(patient.toString().contains("81729"));

        PatientAllergen allergen = new PatientAllergen(ALLERGEN_SECRET,
                null, ALLERGEN_SECRET, patient);
        allergen.setGroup(ALLERGEN_SECRET);
        assertRedacted(allergen.toString());
        assertFalse(allergen.toString().contains("81729"));
    }

    @Test
    public void eventToStringHandlesNullsAndDoesNotRevealDoseOrPatient() {
        EventInstance event = new EventInstance(DateTime.now(), EventType.MEDICATION_INTAKE);
        assertRedacted(event.toString());
        Patient patient = new Patient();
        patient.setName(PATIENT_SECRET);
        patient.setId(38291L);
        event.setPatient(patient);
        event.addParam(EventInstance.PARAM_DOSE, MEDICAL_SECRET);
        assertRedacted(event.toString());
        assertFalse(event.toString().contains("38291"));
        assertTrue(event.toString().contains("completed="));
    }

    @Test
    public void scheduleAndAgendaStubDoNotExposeIntakeTitleOrTime() {
        Patient patient = new Patient();
        patient.setName(PATIENT_SECRET);
        Schedule schedule = new Schedule();
        schedule.setPatient(patient);
        assertRedacted(schedule.toString());
        assertFalse(schedule.toString().contains(PATIENT_SECRET));

        DailyAgendaItemStub item = new DailyAgendaItemStub(null, null);
        item.title = MEDICAL_SECRET;
        item.hasEvents = true;
        assertRedacted(item.toString());
        assertTrue(item.toString().contains("hasEvents=true"));
    }
}
