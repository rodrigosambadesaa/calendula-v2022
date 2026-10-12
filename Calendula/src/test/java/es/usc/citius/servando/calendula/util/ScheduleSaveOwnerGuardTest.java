/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.persistence.Schedule;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ScheduleSaveOwnerGuardTest {
    private static Patient patient(long id) {
        Patient p = new Patient();
        p.setId(id);
        return p;
    }

    private static Medicine medicine(long id, Patient patient) {
        Medicine m = new Medicine("Synthetic medicine", Presentation.PILLS);
        m.setId(id);
        m.setPatient(patient);
        return m;
    }

    @Test
    public void createIsBoundToPatientThatOpenedEditorEvenAfterActiveProfileSwitch() {
        Patient opened = patient(900008001L);
        Patient reloaded = patient(900008001L);
        Patient other = patient(900008002L);
        Medicine owned = medicine(900008003L, reloaded);
        assertTrue(ScheduleSaveOwnerGuard.canSaveForOpenedPatient(
                opened, reloaded, patient(900008001L), owned));
        assertFalse("Switching active patient while executor is queued must reject save",
                ScheduleSaveOwnerGuard.canSaveForOpenedPatient(
                        opened, other, patient(900008001L), owned));
        assertFalse("Deleted original patient cannot authorize a schedule",
                ScheduleSaveOwnerGuard.canSaveForOpenedPatient(opened, reloaded, null, owned));
        owned.setPatient(other);
        assertFalse("A re-assigned medicine must not be attached to another patient",
                ScheduleSaveOwnerGuard.canSaveForOpenedPatient(
                        opened, reloaded, patient(900008001L), owned));
        owned.setPatient(null);
        assertTrue("Legacy unassigned catalog medicine remains supported",
                ScheduleSaveOwnerGuard.canSaveForOpenedPatient(
                        opened, reloaded, patient(900008001L), owned));
        assertFalse("An unsaved medicine cannot enter a prescription",
                ScheduleSaveOwnerGuard.canSaveForOpenedPatient(
                        opened, reloaded, patient(900008001L),
                        medicine(0L, null)));
    }

    @Test
    public void editingRequiresBothCapturedAndCurrentScheduleOwnerToMatch() {
        Patient opened = patient(900008101L);
        Patient other = patient(900008102L);
        Medicine m = medicine(900008103L, opened);
        Schedule stale = new Schedule(m);
        stale.setId(900008104L);
        stale.setPatient(patient(900008101L));
        Schedule fromDb = new Schedule(m);
        fromDb.setId(900008104L);
        fromDb.setPatient(patient(900008101L));
        assertTrue(ScheduleSaveOwnerGuard.canEditSchedule(opened, stale, fromDb));
        fromDb.setPatient(other);
        assertFalse("A reassigned persisted schedule cannot be overwritten",
                ScheduleSaveOwnerGuard.canEditSchedule(opened, stale, fromDb));
        fromDb.setPatient(opened);
        stale.setPatient(other);
        assertFalse("A foreign schedule must not be edited through a stale Intent",
                ScheduleSaveOwnerGuard.canEditSchedule(opened, stale, fromDb));
        stale.setPatient(opened);
        fromDb.setId(900008105L);
        assertFalse("Persisted schedule identity must not change",
                ScheduleSaveOwnerGuard.canEditSchedule(opened, stale, fromDb));
    }
}
