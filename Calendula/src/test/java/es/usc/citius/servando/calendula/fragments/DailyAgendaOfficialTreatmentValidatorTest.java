/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.fragments;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedEntity;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedType;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DosageEntity;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Schedule;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Synthetic imported treatments; never accesses production patient records. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DailyAgendaOfficialTreatmentValidatorTest {

    private Patient patient(long id) {
        Patient p = new Patient();
        p.setId(id);
        return p;
    }

    private Schedule schedule(Patient owner) {
        Schedule s = new Schedule();
        s.setId(900000L);
        s.setPatient(owner);
        s.setActiveMedId(880001L);
        return s;
    }

    private ActiveMedEntity provider(Patient owner) {
        ActiveMedEntity record = new ActiveMedEntity();
        record.setId(880001L);
        record.setPatient(owner);
        record.setType(ActiveMedType.NATIONAL_CODE);
        record.setDosage(new DosageEntity());
        return record;
    }

    @Test
    public void savedMatchingPatientAndProviderIdsAreVerifiedByValue() {
        Patient owner = patient(755555L);
        Schedule schedule = schedule(owner);
        ActiveMedEntity provider = provider(patient(755555L));
        assertNotSame(schedule.getActiveMedId(), provider.getId());
        assertTrue(DailyAgendaOfficialTreatmentValidator.verifiedLink(schedule, provider));
        // Imported medication without instruction entries is still valid.
        assertNull(DailyAgendaOfficialTreatmentValidator.firstInstruction(schedule, provider));
    }

    @Test
    public void wrongPatientOrWrongExternalIdIsRejected() {
        Patient owner = patient(755555L);
        Schedule s = schedule(owner);
        assertFalse(DailyAgendaOfficialTreatmentValidator.verifiedLink(
                s, provider(patient(755556L))));
        ActiveMedEntity wrongId = provider(owner);
        wrongId.setId(123456L);
        assertFalse(DailyAgendaOfficialTreatmentValidator.verifiedLink(s, wrongId));
        assertNull(DailyAgendaOfficialTreatmentValidator.firstInstruction(s, wrongId));
    }

    @Test
    public void missingOrIncompleteProviderRowsAreNotDisplayable() {
        Patient owner = patient(755555L);
        Schedule s = schedule(owner);
        assertFalse(DailyAgendaOfficialTreatmentValidator.verifiedLink(s, null));
        ActiveMedEntity noOwner = provider(owner);
        noOwner.setPatient(null);
        assertFalse(DailyAgendaOfficialTreatmentValidator.verifiedLink(s, noOwner));
        ActiveMedEntity noType = provider(owner);
        noType.setType(null);
        assertFalse(DailyAgendaOfficialTreatmentValidator.verifiedLink(s, noType));
        ActiveMedEntity noDosage = provider(owner);
        noDosage.setDosage(null);
        assertFalse(DailyAgendaOfficialTreatmentValidator.verifiedLink(s, noDosage));
        s.setActiveMedId(null);
        assertFalse(DailyAgendaOfficialTreatmentValidator.verifiedLink(s, provider(owner)));
    }
}
