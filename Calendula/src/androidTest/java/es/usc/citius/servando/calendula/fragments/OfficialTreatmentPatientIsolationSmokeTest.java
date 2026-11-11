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
        owner.setCode("ci-official-patient-one");
        Patient other = new Patient();
        other.setId(970002L);
        other.setCode("ci-official-patient-two");

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
        Patient recycled = new Patient();
        recycled.setId(owner.getId());
        recycled.setCode("ci-restored-different-patient");
        source.setPatient(recycled);
        assertFalse("A reused row ID is not the same provider patient",
                DailyAgendaOfficialTreatmentValidator.verifiedLink(schedule, source));
        assertNull(DailyAgendaOfficialTreatmentValidator.firstInstruction(schedule, source));
        assertFalse(DailyAgendaOfficialTreatmentValidator.verifiedLinkForActivePatient(
                schedule, source, owner));
        source.setPatient(owner);
        assertFalse("Stale active patient must not preview another patient's treatment",
                DailyAgendaOfficialTreatmentValidator.verifiedLinkForActivePatient(
                        schedule, source, recycled));
        recycled.setCode(null);
        source.setPatient(recycled);
        assertFalse("Unidentified provider row must not be considered official",
                DailyAgendaOfficialTreatmentValidator.verifiedLink(schedule, source));
        source.setPatient(owner);
        source.setDosage(null);
        assertFalse(DailyAgendaOfficialTreatmentValidator.verifiedLink(schedule, source));
    }
}
