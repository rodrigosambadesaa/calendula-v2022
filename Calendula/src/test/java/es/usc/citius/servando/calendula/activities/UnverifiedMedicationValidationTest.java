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

        assertFalse(IntakeNotificationMgr.canRenderMedication(event, null));
        assertFalse(IntakeNotificationMgr.canRenderMedication(null, schedule));
        assertFalse("Null Bundle must not crash on dose access",
                IntakeNotificationMgr.canRenderMedication(event, schedule));

        event.addParam(EventInstance.PARAM_DOSE, 1.0d);
        assertTrue("Valid synthetic positive dose and presentation should render",
                IntakeNotificationMgr.canRenderMedication(event, schedule));

        event.addParam(EventInstance.PARAM_DOSE, -1.0d);
        assertFalse("Never display negative dosage",
                IntakeNotificationMgr.canRenderMedication(event, schedule));
        event.addParam(EventInstance.PARAM_DOSE, 0.0d);
        assertFalse("Never present zero as a scheduled intake",
                IntakeNotificationMgr.canRenderMedication(event, schedule));

        event.addParam(EventInstance.PARAM_DOSE, "not-a-dose");
        assertFalse("Malformed quantity must fail closed",
                IntakeNotificationMgr.canRenderMedication(event, schedule));
        event.addParam(EventInstance.PARAM_DOSE, 1.0d);
        medicine.setPresentation(null);
        assertFalse("Missing units must not be invented",
                IntakeNotificationMgr.canRenderMedication(event, schedule));
        medicine.setPresentation(Presentation.PILLS);
        medicine.setName(" ");
        assertFalse("Missing medicine name must not render",
                IntakeNotificationMgr.canRenderMedication(event, schedule));
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
