/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util.alerts;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.persistence.Schedule;

import static org.junit.Assert.*;

/** Pure, synthetic recovery-owner policy; does not read medical databases. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ScheduleAlarmRecoveryIdentityTest {

    private static Patient patient(long id, String code) {
        Patient p = new Patient();
        p.setId(id);
        p.setCode(code);
        return p;
    }

    private static Schedule pending(Medicine medicine, Patient patient) {
        Schedule schedule = new Schedule(medicine);
        schedule.setId(81201L);
        schedule.setPatient(patient);
        schedule.addState(Schedule.ScheduleState.PENDING_REMINDER_RECOVERY);
        schedule.setRecoveryOwnerDigest(ScheduleAlarmRecovery.ownerDigest(patient));
        return schedule;
    }

    @Test
    public void digestIsOpaqueDeterministicAndDistinctByPatientIdentity() {
        Patient owner = patient(81200L, "ci-digest-private-owner");
        String expected = ScheduleAlarmRecovery.ownerDigest(owner);
        assertTrue(expected.matches("sha256-v1:[0-9a-f]{64}"));
        assertFalse(expected.contains(owner.getCode()));
        assertFalse(expected.contains(Long.toString(owner.getId()) + ":"));
        assertEquals(expected, ScheduleAlarmRecovery.ownerDigest(
                patient(81200L, "ci-digest-private-owner")));
        assertNotEquals(expected, ScheduleAlarmRecovery.ownerDigest(
                patient(81200L, "ci-digest-other-owner")));
        assertNotEquals(expected, ScheduleAlarmRecovery.ownerDigest(
                patient(81201L, "ci-digest-private-owner")));
    }

    @Test
    public void identityRejectsOwnerlessRowsAndRecycledSqliteIds() {
        Patient owner = patient(81200L, "ci-recovery-original");
        Patient restored = patient(81200L, "ci-recovery-replacement");
        Medicine medicine = new Medicine("Synthetic medicine", Presentation.PILLS);
        medicine.setId(91100L);
        medicine.setPatient(owner);
        Schedule schedule = pending(medicine, owner);

        assertTrue(ScheduleAlarmRecovery.eligible(
                schedule, owner, owner, medicine, false));
        assertFalse(ScheduleAlarmRecovery.eligible(
                schedule, owner, restored, medicine, false));
        assertFalse(ScheduleAlarmRecovery.eligible(
                schedule, restored, restored, medicine, false));
        assertFalse(ScheduleAlarmRecovery.eligible(
                schedule, owner, owner, medicine, true));

        schedule.setRecoveryOwnerDigest(null);
        assertFalse(ScheduleAlarmRecovery.eligible(
                schedule, owner, owner, medicine, false));
        schedule.setRecoveryOwnerDigest(ScheduleAlarmRecovery.ownerDigest(restored));
        assertFalse(ScheduleAlarmRecovery.eligible(
                schedule, owner, owner, medicine, false));
        schedule.setRecoveryOwnerDigest(ScheduleAlarmRecovery.ownerDigest(owner));

        schedule.addState(Schedule.ScheduleState.BLOCKED);
        assertFalse(ScheduleAlarmRecovery.eligible(
                schedule, owner, owner, medicine, false));
        schedule.removeState(Schedule.ScheduleState.BLOCKED);
        schedule.removeState(Schedule.ScheduleState.PENDING_REMINDER_RECOVERY);
        assertFalse(ScheduleAlarmRecovery.eligible(
                schedule, owner, owner, medicine, false));

        medicine.setPatient(restored);
        schedule.addState(Schedule.ScheduleState.PENDING_REMINDER_RECOVERY);
        assertFalse(ScheduleAlarmRecovery.eligible(
                schedule, owner, owner, medicine, false));
    }

    @Test
    public void incompleteIdentityCannotGenerateRecoveryTokens() {
        for (Patient invalid : new Patient[]{
                patient(81200L, null), patient(81200L, ""),
                patient(81200L, "   "), patient(0L, "ci-code")}) {
            try {
                ScheduleAlarmRecovery.ownerDigest(invalid);
                fail("Unidentified patient must never authorize recovery");
            } catch (IllegalArgumentException expected) {
                // No opaque marker can be created without durable owner proof.
            }
        }
    }
}
