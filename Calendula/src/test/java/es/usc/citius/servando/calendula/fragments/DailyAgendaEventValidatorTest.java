/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

import android.os.Bundle;

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Synthetic test data only; ensures failed identity checks cannot leak doses. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DailyAgendaEventValidatorTest {

    private Patient patient(long id) {
        Patient p = new Patient();
        p.setId(id);
        return p;
    }

    private Schedule schedule(Patient owner, Medicine medicine) {
        Schedule s = new Schedule(medicine);
        s.setId(1234567L);
        s.setPatient(owner);
        return s;
    }

    private EventInstance event(Patient owner) {
        EventInstance e = new EventInstance(DateTime.now(), EventType.MEDICATION_INTAKE);
        e.setId(4567890L);
        e.setRef(1234567L);
        e.setPatient(owner);
        e.addParam(EventInstance.PARAM_DOSE, 1.5d);
        return e;
    }

    @Test
    public void onlyCorrectlyOwnedPositiveDoseIsRenderable() {
        Patient a = patient(100001L);
        Medicine med = new Medicine("Synthetic tablet", Presentation.PILLS);
        med.setPatient(a);
        assertEquals(Double.valueOf(1.5d),
                DailyAgendaEventValidator.verifiedDose(event(patient(100001L)), schedule(a, med)));
    }

    @Test
    public void eventCannotBeDisplayedUnderAnotherPatientsSchedule() {
        Patient a = patient(100001L);
        Patient b = patient(100002L);
        Medicine med = new Medicine("Synthetic tablet B", Presentation.PILLS);
        med.setPatient(b);
        assertNull(DailyAgendaEventValidator.verifiedDose(event(a), schedule(b, med)));

        Schedule ownerSchedule = schedule(a, med);
        assertNull("Medicine owner must also match event owner",
                DailyAgendaEventValidator.verifiedDose(event(a), ownerSchedule));
    }

    @Test
    public void cannotRenderMissingOrUnpersistedClinicalIdentity() {
        Patient a = patient(100001L);
        Medicine med = new Medicine("Synthetic tablet", Presentation.PILLS);
        med.setPatient(a);
        Schedule s = schedule(a, med);
        EventInstance e = event(a);
        assertNull(DailyAgendaEventValidator.verifiedDose(null, s));
        assertNull(DailyAgendaEventValidator.verifiedDose(e, null));
        e.setRef(999999L);
        assertNull(DailyAgendaEventValidator.verifiedDose(e, s));
        e.setRef(s.getId());
        e.setId(null);
        assertNull(DailyAgendaEventValidator.verifiedDose(e, s));
        e.setId(4567890L);
        e.setPatient(null);
        assertNull(DailyAgendaEventValidator.verifiedDose(e, s));
    }

    @Test
    public void invalidOrMissingNumericDoseIsNeverShown() {
        Patient a = patient(100001L);
        Medicine med = new Medicine("Synthetic tablet", Presentation.PILLS);
        med.setPatient(a);
        Schedule s = schedule(a, med);
        for (String invalid : new String[]{"null", "NaN", "Infinity", "-1", "0", "not-a-dose"}) {
            EventInstance e = event(a);
            e.addParam(EventInstance.PARAM_DOSE, invalid);
            assertNull("Invalid value must not be displayed: " + invalid,
                    DailyAgendaEventValidator.verifiedDose(e, s));
        }
        EventInstance missing = new EventInstance(DateTime.now(), EventType.MEDICATION_INTAKE);
        missing.setId(4567890L);
        missing.setRef(s.getId());
        missing.setPatient(a);
        assertNull(DailyAgendaEventValidator.verifiedDose(missing, s));
        Bundle wrongType = new Bundle();
        wrongType.putInt(EventInstance.PARAM_DOSE, 42);
        missing.addParam("Unrelated", "sample");
        missing.getParams().putInt(EventInstance.PARAM_DOSE, 42);
        assertNull(DailyAgendaEventValidator.verifiedDose(missing, s));
    }

    @Test
    public void sharedCatalogMedicineCanBeShownWhenEventAndScheduleOwnersMatch() {
        Patient a = patient(100001L);
        Medicine shared = new Medicine("Synthetic shared catalog", Presentation.PILLS);
        assertEquals(Double.valueOf(1.5d),
                DailyAgendaEventValidator.verifiedDose(event(a), schedule(a, shared)));
    }
}
