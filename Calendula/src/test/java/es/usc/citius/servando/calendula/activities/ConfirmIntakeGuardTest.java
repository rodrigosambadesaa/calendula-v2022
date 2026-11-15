/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedEntity;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedType;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DosageEntity;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventType;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Fault injection with synthetic treatment and patient identities only. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ConfirmIntakeGuardTest {

    private Patient owner(long id) {
        Patient p = new Patient();
        p.setId(id);
        p.setCode("ci-confirm-owner-" + id);
        return p;
    }

    private Schedule schedule(Patient p) {
        Medicine med = new Medicine("Synthetic tablet", Presentation.PILLS);
        med.setPatient(p);
        Schedule s = new Schedule(med);
        s.setId(800001L);
        s.setPatient(p);
        return s;
    }

    private EventInstance intake(Patient p, DateTime dateTime) {
        EventInstance event = new EventInstance(dateTime, EventType.MEDICATION_INTAKE);
        event.setId(810002L);
        event.setRef(800001L);
        event.setPatient(p);
        event.addParam(EventInstance.PARAM_DOSE, 1.5d);
        return event;
    }

    private ActiveMedEntity imported(Patient owner) {
        ActiveMedEntity source = new ActiveMedEntity();
        source.setId(820003L);
        source.setPatient(owner);
        source.setType(ActiveMedType.NATIONAL_CODE);
        source.setDosage(new DosageEntity());
        return source;
    }

    @Test
    public void reusedSqliteIdCannotAuthorizeAnotherProfilesDose() {
        Patient oldOwner = owner(800100L);
        oldOwner.setCode("ci-synthetic-previous-owner");
        Patient newOwner = owner(800100L);
        newOwner.setCode("ci-synthetic-replacement-owner");
        Patient noCode = owner(800100L);
        noCode.setCode(null);
        DateTime when = DateTime.now();

        assertFalse(ConfirmIntakeGuard.samePatientIdentity(oldOwner, newOwner));
        assertFalse(ConfirmIntakeGuard.samePatientIdentity(oldOwner, noCode));
        assertFalse(ConfirmIntakeGuard.samePatientIdentity(noCode, newOwner));
        assertTrue(ConfirmIntakeGuard.samePatientIdentity(newOwner, newOwner));
        Patient secondUnidentified = owner(800100L);
        secondUnidentified.setCode(null);
        assertFalse("Two unidentified legacy profiles cannot authorize a clinical decision",
                ConfirmIntakeGuard.samePatientIdentity(noCode, secondUnidentified));
        secondUnidentified.setCode("  ");
        assertFalse(ConfirmIntakeGuard.samePatientIdentity(noCode, secondUnidentified));

        EventInstance staleView = intake(oldOwner, when);
        EventInstance persisted = intake(newOwner, when);
        assertFalse(ConfirmIntakeGuard.matchesPersistedEvent(staleView, persisted));
        assertFalse(ConfirmIntakeGuard.verified(staleView, newOwner, when,
                schedule(newOwner), null));

        EventInstance validView = intake(newOwner, when);
        assertTrue(ConfirmIntakeGuard.matchesPersistedEvent(validView, persisted));
        assertTrue(ConfirmIntakeGuard.verified(validView, newOwner, when,
                schedule(newOwner), null));
    }

    @Test
    public void legacyOwnerWithoutStableCodeCannotConfirmOrChangeStock() {
        Patient legacy = owner(800100L);
        legacy.setCode(null);
        Patient oldSnapshot = owner(800100L);
        oldSnapshot.setCode(null);
        DateTime date = DateTime.now();
        EventInstance displayed = intake(legacy, date);
        EventInstance persisted = intake(oldSnapshot, date);

        assertFalse(ConfirmIntakeGuard.samePatientIdentity(legacy, oldSnapshot));
        assertFalse(ConfirmIntakeGuard.matchesPersistedEvent(displayed, persisted));
        assertFalse(ConfirmIntakeGuard.verified(
                displayed, legacy, date, schedule(legacy), null));

        legacy.setCode("ci-stable-local-owner");
        oldSnapshot.setCode("ci-stable-local-owner");
        assertTrue("Migrated matching local owner can confirm safely",
                ConfirmIntakeGuard.samePatientIdentity(legacy, oldSnapshot));
        assertTrue(ConfirmIntakeGuard.matchesPersistedEvent(displayed, persisted));
        assertTrue(ConfirmIntakeGuard.verified(
                displayed, legacy, date, schedule(legacy), null));
    }

    @Test
    public void validEventAndDoseBelongToSelectedPatient() {
        Patient p = owner(800100L);
        DateTime date = DateTime.now();
        assertTrue(ConfirmIntakeGuard.verified(
                intake(owner(800100L), date), p, date, schedule(p), null));
    }

    @Test
    public void wrongOwnerWrongTimeAndMalformedDoseCannotBeConfirmed() {
        Patient p = owner(800100L);
        Patient other = owner(800101L);
        DateTime date = DateTime.now();
        Schedule ownSchedule = schedule(p);
        assertFalse(ConfirmIntakeGuard.verified(intake(other, date), p, date,
                ownSchedule, null));
        assertFalse(ConfirmIntakeGuard.verified(intake(p, date.plusHours(1)),
                p, date, ownSchedule, null));
        EventInstance corrupt = intake(p, date);
        corrupt.addParam(EventInstance.PARAM_DOSE, "Infinity");
        assertFalse(ConfirmIntakeGuard.verified(corrupt, p, date, ownSchedule, null));
        assertFalse(ConfirmIntakeGuard.verified(intake(p, date), p, date,
                null, null));
        assertFalse(ConfirmIntakeGuard.verified(intake(p, date), null, date,
                ownSchedule, null));
    }

    @Test
    public void importedPrescriptionNeedsSameOwnerAndPersistedId() {
        Patient p = owner(800100L);
        DateTime date = DateTime.now();
        Schedule s = schedule(p);
        s.setActiveMedId(820003L);
        EventInstance event = intake(p, date);
        assertFalse(ConfirmIntakeGuard.verified(event, p, date, s, null));
        assertFalse(ConfirmIntakeGuard.verified(event, p, date, s,
                imported(owner(800101L))));
        assertTrue(ConfirmIntakeGuard.verified(event, p, date, s,
                imported(owner(800100L))));
        ActiveMedEntity orphan = imported(p);
        orphan.setDosage(null);
        assertFalse(ConfirmIntakeGuard.verified(event, p, date, s, orphan));
    }

    @Test
    public void officialPrescriptionWithoutLiveSourceCannotBeConfirmedAsManualDose() {
        Patient patient = owner(800100L);
        DateTime when = DateTime.now();
        EventInstance event = intake(patient, when);
        Schedule schedule = schedule(patient);

        // A historical imported schedule might have lost its ActiveMed link
        // while retaining the "created from official" flag. Without the
        // guard, this identical dose was incorrectly treated as manual.
        schedule.addState(Schedule.ScheduleState.CREATED_FROM_OFFICIAL);
        assertFalse(ConfirmIntakeGuard.verified(event, patient, when, schedule, null));

        schedule.setActiveMedId(820003L);
        assertFalse(ConfirmIntakeGuard.verified(event, patient, when, schedule, null));
        assertFalse(ConfirmIntakeGuard.verified(event, patient, when, schedule,
                imported(owner(800101L))));
        assertTrue(ConfirmIntakeGuard.verified(event, patient, when, schedule,
                imported(owner(800100L))));
    }

    @Test
    public void recycledOrDelayedRowActionsMustKeepTheirOriginalClinicalSnapshot() {
        Patient p = owner(800100L);
        DateTime date = DateTime.now();
        EventInstance selected = intake(p, date);
        EventInstance other = intake(p, date);
        other.setId(810003L);
        java.util.List<EventInstance> rows = java.util.Arrays.asList(selected, other);

        assertTrue(ConfirmIntakeGuard.matchesVisibleRow(rows, 0, selected));
        assertFalse(ConfirmIntakeGuard.matchesVisibleRow(rows, 1, selected));
        assertFalse(ConfirmIntakeGuard.matchesVisibleRow(rows, -1, selected));
        assertFalse(ConfirmIntakeGuard.matchesVisibleRow(rows, 2, selected));
        assertFalse(ConfirmIntakeGuard.matchesVisibleRow(null, 0, selected));

        EventInstance changed = intake(p, date);
        changed.addParam(EventInstance.PARAM_DOSE, 2.0d);
        assertFalse(ConfirmIntakeGuard.matchesPersistedEvent(selected, changed));
        changed.addParam(EventInstance.PARAM_DOSE, 1.5d);
        changed.setCompleted(true);
        assertFalse(ConfirmIntakeGuard.matchesPersistedEvent(selected, changed));
        changed.setCompleted(false);
        changed.setCancelled(true);
        assertFalse(ConfirmIntakeGuard.matchesPersistedEvent(selected, changed));
        changed.setCancelled(false);
        changed.addParam(EventInstance.PARAM_DOSE, "broken");
        assertFalse(ConfirmIntakeGuard.matchesPersistedEvent(selected, changed));
    }

    @Test
    public void nonpersistedMatchingIdsCannotAuthorizeAnIntakeAction() {
        Patient owner = owner(800100L);
        DateTime at = DateTime.now();
        EventInstance selected = intake(owner, at);
        EventInstance stored = intake(owner(800100L), at);
        assertTrue(ConfirmIntakeGuard.matchesPersistedEvent(selected, stored));

        selected.setId(0L);
        stored.setId(0L);
        assertFalse("Zero event IDs are never persisted clinical authority",
                ConfirmIntakeGuard.matchesPersistedEvent(selected, stored));
        selected.setId(810002L);
        stored.setId(810002L);

        selected.setRef(-1L);
        stored.setRef(-1L);
        assertFalse("Negative schedule references are not actionable",
                ConfirmIntakeGuard.matchesPersistedEvent(selected, stored));
        selected.setRef(800001L);
        stored.setRef(800001L);

        selected.setPatient(owner(0L));
        stored.setPatient(owner(0L));
        assertFalse("Matching unsaved patient objects do not authorize a dose",
                ConfirmIntakeGuard.matchesPersistedEvent(selected, stored));

        selected.setPatient(owner);
        stored.setPatient(owner(800100L));
        selected.setTime(null);
        stored.setTime(null);
        assertFalse("Missing appointment time cannot authorize a dose",
                ConfirmIntakeGuard.matchesPersistedEvent(selected, stored));

        selected.setTime(at);
        stored.setTime(at);
        assertTrue(ConfirmIntakeGuard.matchesPersistedEvent(selected, stored));
    }

    @Test
    public void staleSelectionCannotSubstituteChangedClinicalEvent() {
        Patient p = owner(800100L);
        DateTime date = DateTime.now();
        EventInstance selection = intake(p, date);
        EventInstance stored = intake(owner(800100L), date);
        assertTrue(ConfirmIntakeGuard.matchesPersistedEvent(selection, stored));
        stored.setPatient(owner(800101L));
        assertFalse(ConfirmIntakeGuard.matchesPersistedEvent(selection, stored));
        stored.setPatient(p);
        stored.setRef(800099L);
        assertFalse(ConfirmIntakeGuard.matchesPersistedEvent(selection, stored));
        stored.setRef(800001L);
        stored.setTime(date.plusMinutes(1));
        assertFalse(ConfirmIntakeGuard.matchesPersistedEvent(selection, stored));
        assertFalse(ConfirmIntakeGuard.matchesPersistedEvent(selection, null));
    }
}
