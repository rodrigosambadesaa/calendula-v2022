/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedEntity;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedType;
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DosageEntity;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Schedule;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Actual Android runtime; disposable in-memory synthetic identities only. */
@RunWith(AndroidJUnit4.class)
public class OfficialNotificationSourceSmokeTest {

    @Test
    public void boundNotificationMustNotShowAnotherPatientsOfficialTreatment() {
        Patient owner = new Patient();
        owner.setId(95011001L);
        Patient other = new Patient();
        other.setId(95011002L);
        Schedule schedule = new Schedule();
        schedule.setPatient(owner);
        schedule.setActiveMedId(95011003L);
        schedule.addState(Schedule.ScheduleState.CREATED_FROM_OFFICIAL);

        ActiveMedEntity imported = new ActiveMedEntity();
        imported.setId(95011003L);
        imported.setType(ActiveMedType.DCPF);
        imported.setDosage(new DosageEntity());
        imported.setPatient(other);

        assertFalse(IntakeNotificationMgr.canRenderOfficialSource(schedule, imported));
        imported.setPatient(owner);
        assertTrue(IntakeNotificationMgr.canRenderOfficialSource(schedule, imported));
        imported.setDosage(null);
        assertFalse(IntakeNotificationMgr.canRenderOfficialSource(schedule, imported));
    }

    @Test
    public void manualNotificationDoesNotNeedImportedRecord() {
        Schedule manual = new Schedule();
        assertTrue(IntakeNotificationMgr.canRenderOfficialSource(manual, null));
        manual.addState(Schedule.ScheduleState.CREATED_FROM_OFFICIAL);
        assertFalse(IntakeNotificationMgr.canRenderOfficialSource(manual, null));
    }
}
