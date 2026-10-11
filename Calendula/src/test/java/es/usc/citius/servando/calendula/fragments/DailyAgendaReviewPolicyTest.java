/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Patient-scoped generic warning rules, with synthetic identity only. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DailyAgendaReviewPolicyTest {

    private Patient patient(long id) {
        Patient p = new Patient();
        p.setId(id);
        return p;
    }

    private Schedule schedule(Patient owner) {
        Medicine med = new Medicine("Synthetic med", Presentation.PILLS);
        med.setPatient(owner);
        Schedule s = new Schedule(med);
        s.setPatient(owner);
        s.setId(775000L);
        return s;
    }

    private EventInstance event(Patient owner, String dose) {
        EventInstance e = new EventInstance(DateTime.now(), EventType.MEDICATION_INTAKE);
        e.setId(988000L);
        e.setRef(775000L);
        e.setPatient(owner);
        if (dose != null) {
            e.addParam(EventInstance.PARAM_DOSE, dose);
        }
        return e;
    }

    @Test
    public void validMedicationMustNotShowWarning() {
        Patient a = patient(100100L);
        assertFalse(DailyAgendaReviewPolicy.needsReview(event(a, "1.5"), schedule(a), a));
    }

    @Test
    public void invalidDoseOrOrphanReferenceShowsWarningOnlyForOwner() {
        Patient a = patient(100100L);
        Patient b = patient(100101L);
        assertTrue(DailyAgendaReviewPolicy.needsReview(event(a, "NaN"), schedule(a), a));
        assertFalse(DailyAgendaReviewPolicy.needsReview(event(a, "NaN"), schedule(a), b));
        assertTrue(DailyAgendaReviewPolicy.needsReview(event(a, null), null, a));
        assertFalse(DailyAgendaReviewPolicy.needsReview(event(a, null), null, b));
    }

    @Test
    public void crossedOwnersNeverExposeAnotherPatientsAlert() {
        Patient a = patient(100100L);
        Patient b = patient(100101L);
        EventInstance eventOwnedByA = event(a, "1.0");
        assertTrue(DailyAgendaReviewPolicy.needsReview(eventOwnedByA, schedule(b), a));
        assertFalse(DailyAgendaReviewPolicy.needsReview(eventOwnedByA, schedule(b), b));
    }

    @Test
    public void unknownOwnerRequiresScheduleMatchingActivePatient() {
        Patient a = patient(100100L);
        Patient b = patient(100101L);
        EventInstance unknownOwner = event(null, null);
        assertTrue(DailyAgendaReviewPolicy.needsReview(unknownOwner, schedule(a), a));
        assertFalse(DailyAgendaReviewPolicy.needsReview(unknownOwner, schedule(a), b));
        assertFalse(DailyAgendaReviewPolicy.needsReview(unknownOwner, null, a));
        assertFalse(DailyAgendaReviewPolicy.needsReview(unknownOwner, schedule(a), null));
    }
}
