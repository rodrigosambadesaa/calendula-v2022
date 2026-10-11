/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedEntity;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedType;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DosageEntity;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Schedule;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Device-runtime regression: never show another patient's imported treatment. */
@RunWith(AndroidJUnit4.class)
public class OfficialTreatmentPatientIsolationSmokeTest {

    @Test
    public void externalRecordOwnerAndIdentityMustMatchOnAndroid() {
        Patient owner = new Patient();
        owner.setId(970001L);
        Patient other = new Patient();
        other.setId(970002L);

        Schedule schedule = new Schedule();
        schedule.setId(990001L);
        schedule.setPatient(owner);
        schedule.setActiveMedId(980001L);

        ActiveMedEntity source = new ActiveMedEntity();
        source.setId(980001L);
        source.setType(ActiveMedType.DCPF);
        source.setDosage(new DosageEntity());
        source.setPatient(other);
        assertFalse(DailyAgendaOfficialTreatmentValidator.verifiedLink(schedule, source));
        assertNull(DailyAgendaOfficialTreatmentValidator.firstInstruction(schedule, source));

        source.setPatient(owner);
        assertTrue(DailyAgendaOfficialTreatmentValidator.verifiedLink(schedule, source));
        source.setDosage(null);
        assertFalse(DailyAgendaOfficialTreatmentValidator.verifiedLink(schedule, source));
    }
}
