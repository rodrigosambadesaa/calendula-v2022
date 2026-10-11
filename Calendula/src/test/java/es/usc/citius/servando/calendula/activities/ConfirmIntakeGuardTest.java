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
