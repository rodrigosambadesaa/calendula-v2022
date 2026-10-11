/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;

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

/** On-device validation of two synthetic patient profiles; no clinical writes. */
@RunWith(AndroidJUnit4.class)
public class ConfirmIntakePatientIsolationSmokeTest {

    @Test
    public void confirmationRejectsCrossPatientAndMalformedDosesOnAndroid() {
        Patient owner = new Patient();
        owner.setId(700001L);
        Patient other = new Patient();
        other.setId(700002L);
        Medicine medicine = new Medicine("Synthetic confirmation medicine", Presentation.PILLS);
        medicine.setPatient(owner);
        Schedule schedule = new Schedule(medicine);
        schedule.setId(710001L);
        schedule.setPatient(owner);
        DateTime slot = DateTime.now().withMillisOfSecond(0);
        EventInstance event = new EventInstance(slot, EventType.MEDICATION_INTAKE);
        event.setId(720001L);
        event.setRef(schedule.getId());
        event.setPatient(owner);
        event.addParam(EventInstance.PARAM_DOSE, 2.0d);

        assertTrue(ConfirmIntakeGuard.verified(event, owner, slot, schedule, null));
        assertFalse(ConfirmIntakeGuard.verified(event, other, slot, schedule, null));

        schedule.setActiveMedId(730001L);
        ActiveMedEntity imported = new ActiveMedEntity();
        imported.setId(730001L);
        imported.setType(ActiveMedType.NATIONAL_CODE);
        imported.setDosage(new DosageEntity());
        imported.setPatient(other);
        assertFalse("Imported dose from the wrong patient must not be confirmed",
                ConfirmIntakeGuard.verified(event, owner, slot, schedule, imported));
        imported.setPatient(owner);
        assertTrue(ConfirmIntakeGuard.verified(event, owner, slot, schedule, imported));

        event.addParam(EventInstance.PARAM_DOSE, "NaN");
        assertFalse("Non-finite dose must not be confirmed",
                ConfirmIntakeGuard.verified(event, owner, slot, schedule, imported));
    }
}
