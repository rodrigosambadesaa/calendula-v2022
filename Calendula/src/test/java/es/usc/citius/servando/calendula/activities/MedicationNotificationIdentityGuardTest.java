/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class MedicationNotificationIdentityGuardTest {
    private EventReminder reminder(EventType type, Long patientId, Long reminderId) {
        DateTime when = new DateTime(2026, 10, 15, 10, 30);
        EventReminder reminder = new EventReminder(when, type);
        reminder.setId(reminderId);
        Patient owner = new Patient();
        owner.setId(patientId);
        reminder.setPatient(owner);
        reminder.setNextTime(when);
        return reminder;
    }

    @Test
    public void actionableMedicationNoticeNeedsValidPersistedOwnership() {
        assertFalse(IntakeNotificationMgr.isVerifiedMedicationReminderIdentity(null));
        assertTrue(IntakeNotificationMgr.isVerifiedMedicationReminderIdentity(
                reminder(EventType.MEDICATION_INTAKE, 1001L, 2002L)));
        assertFalse(IntakeNotificationMgr.isVerifiedMedicationReminderIdentity(
                reminder(EventType.MEDICATION_INTAKE, null, 2002L)));
        assertFalse(IntakeNotificationMgr.isVerifiedMedicationReminderIdentity(
                reminder(EventType.MEDICATION_INTAKE, 0L, 2002L)));
        assertFalse(IntakeNotificationMgr.isVerifiedMedicationReminderIdentity(
                reminder(EventType.MEDICATION_INTAKE, -3L, 2002L)));
        assertFalse(IntakeNotificationMgr.isVerifiedMedicationReminderIdentity(
                reminder(EventType.MEDICATION_INTAKE, 1001L, null)));
        assertFalse(IntakeNotificationMgr.isVerifiedMedicationReminderIdentity(
                reminder(EventType.MEDICATION_INTAKE, 1001L, 0L)));
    }

    @Test
    public void unrelatedEventTypesAreNeverRenderedAsMedicationIntakeActions() {
        assertFalse(IntakeNotificationMgr.isVerifiedMedicationReminderIdentity(
                reminder(null, 1001L, 2002L)));
        assertFalse(IntakeNotificationMgr.isVerifiedMedicationReminderIdentity(
                reminder(EventType.STOCK_REMINDER, 1001L, 2002L)));
        assertFalse(IntakeNotificationMgr.isVerifiedMedicationReminderIdentity(
                reminder(EventType.PHARMACY_REMINDER, 1001L, 2002L)));
    }
}
