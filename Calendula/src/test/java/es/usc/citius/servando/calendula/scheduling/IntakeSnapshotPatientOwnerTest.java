package es.usc.citius.servando.calendula.scheduling;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;

import static org.junit.Assert.*;

/** Synthetic in-memory consent models: no persisted patients, stock or alarms. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class IntakeSnapshotPatientOwnerTest {
    private static final DateTime WHEN = new DateTime(2026, 10, 28, 17, 0);

    private static Patient patient(long id) {
        Patient patient = new Patient();
        patient.setId(id);
        return patient;
    }

    private static EventInstance event(long id, long ref, Patient patient) {
        EventInstance event = new EventInstance(WHEN, EventType.MEDICATION_INTAKE);
        event.setId(id);
        event.setRef(ref);
        event.setPatient(patient);
        event.addParam(EventInstance.PARAM_DOSE, 1.0d);
        return event;
    }

    private static EventReminder reminder(Patient patient) {
        EventReminder reminder = new EventReminder(WHEN, EventType.MEDICATION_INTAKE);
        reminder.setId(900L);
        reminder.setPatient(patient);
        reminder.setNextTime(WHEN);
        return reminder;
    }

    private static Schedule schedule(long id, Patient patient) {
        Medicine medicine = new Medicine("Synthetic consent medicine", Presentation.PILLS);
        medicine.setId(id + 1000L);
        medicine.setPatient(patient);
        Schedule schedule = new Schedule(medicine);
        schedule.setId(id);
        schedule.setPatient(patient);
        return schedule;
    }

    @Test
    public void oneOwnerAndDistinctPersistedEventsHaveStableDigest() {
        Patient owner = patient(101L);
        EventInstance first = event(11L, 21L, owner);
        EventInstance second = event(12L, 22L, owner);
        String expected = IntakeSnapshot.fingerprint(Arrays.asList(first, second));
        assertNotNull(expected);
        assertEquals(64, expected.length());
        assertEquals(expected, IntakeSnapshot.fingerprint(Arrays.asList(second, first)));
    }

    @Test
    public void mixedOrInvalidOwnersAndReferencesCannotCreateConsentDigest() {
        Patient a = patient(101L);
        EventInstance first = event(11L, 21L, a);
        EventInstance foreign = event(12L, 22L, patient(102L));
        assertNull(IntakeSnapshot.fingerprint(Arrays.asList(first, foreign)));
        foreign.setPatient(patient(101L));
        assertNotNull(IntakeSnapshot.fingerprint(Arrays.asList(first, foreign)));

        first.setRef(0L);
        assertNull(IntakeSnapshot.fingerprint(Collections.singletonList(first)));
        first.setRef(21L);
        first.setPatient(patient(-1L));
        assertNull(IntakeSnapshot.fingerprint(Collections.singletonList(first)));
        first.setPatient(a);
        assertNotNull(IntakeSnapshot.fingerprint(Collections.singletonList(first)));
    }

    @Test
    public void reminderOwnerMustMatchEveryVisibleMedicationEvent() {
        Patient a = patient(101L);
        Patient b = patient(102L);
        EventInstance first = event(11L, 21L, a);
        Map<Long, Schedule> rendered = new HashMap<>();
        rendered.put(first.getId(), schedule(21L, a));
        String valid = IntakeSnapshot.fingerprint(
                Collections.singletonList(first), reminder(a), rendered);
        assertNotNull("One matching owner must remain actionable", valid);
        assertNull("A reminder for patient B cannot authorize patient A's dose",
                IntakeSnapshot.fingerprint(
                        Collections.singletonList(first), reminder(b), rendered));
        assertNull("Invalid reminder owner must not authorize any dose",
                IntakeSnapshot.fingerprint(
                        Collections.singletonList(first), reminder(patient(0L)), rendered));
    }
}
