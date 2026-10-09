/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import android.app.Notification;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.Presentation;
import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.Schedule;
import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.EventReminder;
import es.usc.citius.servando.calendula.scheduling.model.EventType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** No medical database, patient details or genuine prescription used. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class UnverifiedMedicationValidationTest {

    private static EventInstance syntheticEvent() {
        EventInstance event = new EventInstance(DateTime.now().plusHours(5),
                EventType.MEDICATION_INTAKE);
        event.setRef(9000003301L);
        return event;
    }

    @Test
    public void malformedPatientScheduleOrDoseDoesNotPassValidation() {
        EventInstance event = syntheticEvent();
        Medicine medicine = new Medicine("Synthetic test medicine", Presentation.PILLS);
        Schedule schedule = new Schedule(medicine);
        Patient patient = new Patient();
        patient.setId(9000003311L);
        event.setPatient(patient);
        schedule.setPatient(patient);

        assertFalse(IntakeNotificationMgr.canRenderMedication(event, null, patient));
        assertFalse(IntakeNotificationMgr.canRenderMedication(null, schedule, patient));
        assertFalse("Null Bundle must not crash on dose access",
                IntakeNotificationMgr.canRenderMedication(event, schedule, patient));

        event.addParam(EventInstance.PARAM_DOSE, 1.0d);
        assertTrue("Valid synthetic positive dose and presentation should render",
                IntakeNotificationMgr.canRenderMedication(event, schedule, patient));

        event.addParam(EventInstance.PARAM_DOSE, -1.0d);
        assertFalse("Never display negative dosage",
                IntakeNotificationMgr.canRenderMedication(event, schedule, patient));
        event.addParam(EventInstance.PARAM_DOSE, 0.0d);
        assertFalse("Never present zero as a scheduled intake",
                IntakeNotificationMgr.canRenderMedication(event, schedule, patient));

        event.addParam(EventInstance.PARAM_DOSE, "not-a-dose");
        assertFalse("Malformed quantity must fail closed",
                IntakeNotificationMgr.canRenderMedication(event, schedule, patient));
        event.getParams().putLong(EventInstance.PARAM_DOSE, 3L);
        assertFalse("Non-String legacy Bundle dosage must not crash or display",
                IntakeNotificationMgr.canRenderMedication(event, schedule, patient));
        event.addParam(EventInstance.PARAM_DOSE, 1.0d);
        medicine.setPresentation(null);
        assertFalse("Missing units must not be invented",
                IntakeNotificationMgr.canRenderMedication(event, schedule, patient));
        medicine.setPresentation(Presentation.PILLS);
        medicine.setName(" ");
        assertFalse("Missing medicine name must not render",
                IntakeNotificationMgr.canRenderMedication(event, schedule, patient));
        medicine.setName("Synthetic test medicine");
        Patient another = new Patient();
        another.setId(9000003312L);
        schedule.setPatient(another);
        assertFalse("A reference to another patient's prescription must not render",
                IntakeNotificationMgr.canRenderMedication(event, schedule, patient));
    }

    @Test
    public void officialNotificationMustNotReadMissingOrCorruptNumericDose() {
        EventInstance event = syntheticEvent();
        Medicine medicine = new Medicine("Synthetic verified catalog medicine", Presentation.PILLS);
        Schedule schedule = new Schedule(medicine);
        Patient patient = new Patient();
        patient.setId(9000003313L);
        event.setPatient(patient);
        schedule.setPatient(patient);
        schedule.addState(Schedule.ScheduleState.CREATED_FROM_OFFICIAL);

        // Official schedules render their verified label, not a numeric
        // manually-entered dose; these legacy records may have no Bundle.
        assertTrue(IntakeNotificationMgr.canRenderMedication(event, schedule, patient));
        assertNull("No attempt to deserialize a missing official numeric dose",
                IntakeNotificationMgr.doseForNotification(event, schedule));
        event.addParam(EventInstance.PARAM_DOSE, "not-a-number");
        assertNull("A corrupted unused dose must not crash official notification",
                IntakeNotificationMgr.doseForNotification(event, schedule));

        // Manually entered doses continue using the validated numeric value.
        schedule.removeState(Schedule.ScheduleState.CREATED_FROM_OFFICIAL);
        assertFalse(IntakeNotificationMgr.canRenderMedication(event, schedule, patient));
        event.addParam(EventInstance.PARAM_DOSE, 2.5d);
        assertTrue(IntakeNotificationMgr.canRenderMedication(event, schedule, patient));
        assertEquals(2.5d,
                IntakeNotificationMgr.doseForNotification(event, schedule), 0.0001d);
    }

    @Test
    public void fallbackAlertHasNoTakeOrCancelMedicationActions() {
        Context context = ApplicationProvider.getApplicationContext();
        DateTime time = DateTime.now().plusHours(7);
        EventReminder reminder = new EventReminder(time, EventType.MEDICATION_INTAKE);
        reminder.setId(9000003302L);
        reminder.setNextTime(time);
        Notification alert = IntakeNotificationMgr.buildUnverifiedMedicationNotification(
                context, reminder);
        assertNotNull(alert);
        assertNotNull("The review alert must open Calendula", alert.contentIntent);
        assertEquals(context.getString(R.string.medication_unverified_title),
                alert.extras.getString(Notification.EXTRA_TITLE));
        assertEquals(context.getString(R.string.medication_unverified_description),
                alert.extras.getString(Notification.EXTRA_TEXT));
        assertTrue("Generic alert must never expose a 'taken' or 'cancel' action",
                alert.actions == null || alert.actions.length == 0);
        alert.contentIntent.cancel();
    }
}
