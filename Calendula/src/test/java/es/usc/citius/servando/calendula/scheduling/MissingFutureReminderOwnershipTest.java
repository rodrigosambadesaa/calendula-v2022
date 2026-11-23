/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventType;

import static org.junit.Assert.*;

/**
 * Safety policy for recovering a committed dose without its reminder row.
 * Synthetic models only; no patient database or Android alarm service.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class MissingFutureReminderOwnershipTest {
    private static Patient patient(long id, String code) {
        Patient p = new Patient();
        p.setId(id);
        p.setCode(code);
        return p;
    }

    @Test
    public void futureReminderRecoveryRejectsForeignAndUnreviewedClinicalRows() {
        Patient original = patient(8201, "ci-verified-owner");
        Patient recycled = patient(8201, "ci-recycled-owner");
        Medicine medicine = new Medicine("Synthetic treatment", Presentation.PILLS);
        medicine.setId(9201L);
        medicine.setPatient(original);
        Schedule schedule = new Schedule(medicine);
        schedule.setId(10201L);
        schedule.setPatient(original);
        EventInstance dose = new EventInstance(
                DateTime.now().plusHours(5), EventType.MEDICATION_INTAKE);
        dose.setId(30001L);
        dose.setRef(schedule.getId());
        dose.setPatient(original);
        assertTrue(Agenda.safeMissingReminderCandidate(
                dose, schedule, medicine, original, false));

        assertFalse(Agenda.safeMissingReminderCandidate(
                dose, schedule, medicine, recycled, false));
        dose.setPatient(recycled);
        assertFalse(Agenda.safeMissingReminderCandidate(
                dose, schedule, medicine, original, false));
        dose.setPatient(original);
        medicine.setPatient(recycled);
        assertFalse(Agenda.safeMissingReminderCandidate(
                dose, schedule, medicine, original, false));
        medicine.setPatient(original);
        assertFalse(Agenda.safeMissingReminderCandidate(
                dose, schedule, medicine, original, true));

        schedule.addState(Schedule.ScheduleState.BLOCKED);
        assertFalse(Agenda.safeMissingReminderCandidate(
                dose, schedule, medicine, original, false));
        schedule.removeState(Schedule.ScheduleState.BLOCKED);
        for (Schedule.ScheduleState waiting : new Schedule.ScheduleState[]{
                Schedule.ScheduleState.PENDING_REVIEW_AFTER_UPDATE,
                Schedule.ScheduleState.PENDING_REVIEW_AFTER_DELETE,
                Schedule.ScheduleState.PENDING_REMINDER_RECOVERY}) {
            schedule.addState(waiting);
            assertFalse(Agenda.safeMissingReminderCandidate(
                    dose, schedule, medicine, original, false));
            schedule.removeState(waiting);
        }

        dose.setCompleted(true);
        assertFalse(Agenda.safeMissingReminderCandidate(
                dose, schedule, medicine, original, false));
        dose.setCompleted(false);
        dose.setCancelled(true);
        assertFalse(Agenda.safeMissingReminderCandidate(
                dose, schedule, medicine, original, false));
        dose.setCancelled(false);

        schedule.setRecoveryOwnerDigest("sha256-v1:synthetic");
        assertFalse(Agenda.safeMissingReminderCandidate(
                dose, schedule, medicine, original, false));
        schedule.setRecoveryOwnerDigest(null);

        dose.setRef(10202L);
        assertFalse(Agenda.safeMissingReminderCandidate(
                dose, schedule, medicine, original, false));
        dose.setRef(schedule.getId());
        dose.setId(null);
        assertFalse(Agenda.safeMissingReminderCandidate(
                dose, schedule, medicine, original, false));
    }

    @Test
    public void ownerlessAndUnassignedEventsCannotAuthorizeClinicalRearm() {
        Patient owner = patient(8201, "ci-medication-owner");
        Medicine medicine = new Medicine("Synthetic treatment", Presentation.PILLS);
        medicine.setId(9201L);
        medicine.setPatient(owner);
        Schedule schedule = new Schedule(medicine);
        schedule.setId(10201L);
        schedule.setPatient(owner);
        EventInstance dose = new EventInstance(
                DateTime.now().plusHours(2), EventType.MEDICATION_INTAKE);
        dose.setId(30001L);
        dose.setRef(schedule.getId());
        assertFalse(Agenda.safeMissingReminderCandidate(
                dose, schedule, medicine, owner, false));
        dose.setPatient(owner);
        schedule.setPatient(patient(8201, ""));
        assertFalse(Agenda.safeMissingReminderCandidate(
                dose, schedule, medicine, owner, false));
        schedule.setPatient(owner);
        dose.setRef(null);
        assertFalse(Agenda.safeMissingReminderCandidate(
                dose, schedule, medicine, owner, false));
    }
}
