/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.scheduling;

import org.joda.time.DateTime;
import org.junit.Test;

import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Pure confirmation-action predicates; never touches a real patient's data. */
public class AlarmNotificationActionValidationTest {

    private static EventReminder medicationReminder() {
        EventReminder reminder = new EventReminder(
                DateTime.now().plusMinutes(10), EventType.MEDICATION_INTAKE);
        reminder.setId(9000008111L);
        Patient owner = new Patient();
        owner.setId(9000008112L);
        reminder.setPatient(owner);
        return reminder;
    }

    @Test
    public void onlyIdentifiedAssignedMedicationReminderMayConfirmDoses() {
        EventReminder valid = medicationReminder();
        assertTrue(AlarmIntentService.isActionableMedicationReminder(valid));

        assertFalse(AlarmIntentService.isActionableMedicationReminder(null));
        valid.setId(null);
        assertFalse(AlarmIntentService.isActionableMedicationReminder(valid));
        valid.setId(-1L);
        assertFalse(AlarmIntentService.isActionableMedicationReminder(valid));
        valid.setId(9000008111L);

        // A pharmacy or other reminder must not confirm medication stock just
        // because a medicine event exists for the same patient and time.
        valid.setEventType(EventType.PHARMACY_REMINDER);
        assertFalse(AlarmIntentService.isActionableMedicationReminder(valid));
        valid.setEventType(EventType.MEDICATION_INTAKE);

        valid.setPatient(null);
        assertFalse(AlarmIntentService.isActionableMedicationReminder(valid));
        Patient unsaved = new Patient();
        valid.setPatient(unsaved);
        assertFalse(AlarmIntentService.isActionableMedicationReminder(valid));
        Patient invalid = new Patient();
        invalid.setId(0L);
        valid.setPatient(invalid);
        assertFalse(AlarmIntentService.isActionableMedicationReminder(valid));
        invalid.setId(-1L);
        assertFalse(AlarmIntentService.isActionableMedicationReminder(valid));
        Patient identified = new Patient();
        identified.setId(9000008112L);
        valid.setPatient(identified);

        valid.setDateTime(null);
        assertFalse(AlarmIntentService.isActionableMedicationReminder(valid));
    }
}
