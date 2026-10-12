/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

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
import static org.junit.Assert.assertTrue;

/** Offline synthetic association checks. Does not touch clinical SQLite rows. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class OfficialNotificationSourceValidationTest {
    private static Patient patient(long id) {
        Patient patient = new Patient();
        patient.setId(id);
        return patient;
    }

    @Test
    public void officialNotificationNeedsCurrentMatchingSourceAndPatient() {
        Patient owner = patient(93011001L);
        Patient other = patient(93011002L);
        Schedule schedule = new Schedule();
        schedule.setPatient(owner);
        schedule.addState(Schedule.ScheduleState.CREATED_FROM_OFFICIAL);

        assertFalse("An orphan official schedule cannot claim a prescription",
                IntakeNotificationMgr.canRenderOfficialSource(schedule, null));
        schedule.setActiveMedId(93011003L);
        assertFalse("A missing imported source is not verified",
                IntakeNotificationMgr.canRenderOfficialSource(schedule, null));

        ActiveMedEntity source = new ActiveMedEntity();
        source.setId(93011003L);
        source.setType(ActiveMedType.DCPF);
        source.setDosage(new DosageEntity());
        source.setPatient(other);
        assertFalse("Another patient's treatment must never be shown",
                IntakeNotificationMgr.canRenderOfficialSource(schedule, source));

        source.setPatient(owner);
        source.setId(93011004L);
        assertFalse("A mismatched prescription ID is not the selected treatment",
                IntakeNotificationMgr.canRenderOfficialSource(schedule, source));

        source.setId(93011003L);
        source.setDosage(null);
        assertFalse("Incomplete imported dosage cannot authorize an official label",
                IntakeNotificationMgr.canRenderOfficialSource(schedule, source));

        source.setDosage(new DosageEntity());
        assertTrue("Correctly linked official treatment remains displayable",
                IntakeNotificationMgr.canRenderOfficialSource(schedule, source));
    }

    @Test
    public void ordinaryManualScheduleDoesNotRequireAnImportedSource() {
        Schedule manual = new Schedule();
        manual.setPatient(patient(93011005L));
        assertTrue(IntakeNotificationMgr.canRenderOfficialSource(manual, null));
        assertFalse(IntakeNotificationMgr.canRenderOfficialSource(null, null));

        // A provider-bound schedule requires verification even when its
        // CREATED_FROM_OFFICIAL bit was lost in a legacy SQLite migration.
        manual.setActiveMedId(93011006L);
        assertFalse(IntakeNotificationMgr.canRenderOfficialSource(manual, null));
    }
}
